package cn.keking.utils;

import cn.keking.model.FileAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验 HTML 中引用的本地/网络 JS、CSS 资源是否可访问，用于预览前拦截缺失依赖的页面。
 */
public final class HtmlReferencedResourceChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlReferencedResourceChecker.class);
    private static final int MAX_REFS = 64;

    private static final Pattern SCRIPT_SRC = Pattern.compile(
            "<script\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern LINK_TAG = Pattern.compile("<link\\b([^>]+)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern HREF_ATTR = Pattern.compile("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private HtmlReferencedResourceChecker() {
    }

    /**
     * @return 错误提示；校验通过返回 {@code null}
     */
    public static String checkRequiredResources(String htmlContent, String documentUrl, FileAttribute fileAttribute) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return null;
        }
        Set<String> refs = new HashSet<>();
        Matcher sm = SCRIPT_SRC.matcher(htmlContent);
        while (sm.find()) {
            refs.add(sm.group(1).trim());
            if (refs.size() > MAX_REFS) {
                return "HTML 中外链脚本或样式过多，超过校验上限";
            }
        }
        Matcher lm = LINK_TAG.matcher(htmlContent);
        while (lm.find()) {
            String attrs = lm.group(1);
            if (!attrs.toLowerCase().contains("stylesheet")) {
                continue;
            }
            Matcher hm = HREF_ATTR.matcher(attrs);
            if (hm.find()) {
                refs.add(hm.group(1).trim());
            }
            if (refs.size() > MAX_REFS) {
                return "HTML 中外链脚本或样式过多，超过校验上限";
            }
        }
        Set<String> checked = new HashSet<>();
        for (String ref : refs) {
            if (ref.isEmpty()) {
                continue;
            }
            String lower = ref.toLowerCase();
            if (lower.startsWith("data:") || lower.startsWith("javascript:") || ref.startsWith("#")) {
                continue;
            }
            String resolved;
            try {
                resolved = resolveReference(ref, documentUrl);
            } catch (Exception e) {
                LOGGER.debug("解析 HTML 资源引用失败 ref={} base={}", ref, documentUrl, e);
                return "HTML 引用的脚本或样式地址无效: " + ref;
            }
            if (resolved == null) {
                continue;
            }
            if (!checked.add(resolved)) {
                continue;
            }
            if (!resourceExists(resolved, fileAttribute)) {
                LOGGER.debug("HTML 引用的脚本或样式无法访问 url={}", resolved);
                return UnsupportedPreviewFriendlyDetail.MSG_HTML_RESOURCES_UNAVAILABLE;
            }
        }
        return null;
    }

    private static String resolveReference(String ref, String documentUrl) throws Exception {
        String trimmed = ref.trim();
        URI refUri = URI.create(trimmed);
        if (refUri.isAbsolute()) {
            return refUri.normalize().toString();
        }
        URI base = WebUtils.normalizedURL(documentUrl).toURI();
        return base.resolve(refUri).normalize().toString();
    }

    private static boolean resourceExists(String resolvedUrl, FileAttribute fileAttribute) {
        try {
            URL url = WebUtils.normalizedURL(resolvedUrl);
            String protocol = url.getProtocol();
            if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
                return HttpRequestUtils.probeUrlReadable(resolvedUrl, fileAttribute);
            }
            if ("file".equalsIgnoreCase(protocol)) {
                Path p = Paths.get(url.toURI());
                return Files.isRegularFile(p);
            }
        } catch (Exception e) {
            LOGGER.debug("校验资源是否存在时异常 url={}", resolvedUrl, e);
            return false;
        }
        return true;
    }
}
