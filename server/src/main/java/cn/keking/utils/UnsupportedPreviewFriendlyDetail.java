package cn.keking.utils;

/**
 * 「暂不支持预览」页面向用户展示的错误详情：优先人类可读语义，无法归类为安全说明的文字则不在页面上展示。
 */
public final class UnsupportedPreviewFriendlyDetail {

    /**
     * HTML 引用的脚本/样式文件无法拉取时的统一说明。
     */
    public static final String MSG_HTML_RESOURCES_UNAVAILABLE =
            "预览所需的脚本或样式资源无法访问，可能是资源未随文件一同上传，或当前网络环境无法连接至资源服务器。";

    private static final String LEGACY_HTML_TOO_MANY = "HTML 中外链脚本或样式过多，超过校验上限";

    private UnsupportedPreviewFriendlyDetail() {
    }

    /**
     * @return 可展示给用户的说明；无需展示时返回 {@code null} 或空串（调用方再统一处理）
     */
    public static String toDisplayDetail(String raw) {
        if (raw == null) {
            return null;
        }
        String err = raw.trim();
        if (err.isEmpty()) {
            return null;
        }

        if (LEGACY_HTML_TOO_MANY.equals(err)) {
            return null;
        }
        if (err.startsWith("HTML 引用的脚本或样式地址无效")) {
            return null;
        }
        if (MSG_HTML_RESOURCES_UNAVAILABLE.equals(err)) {
            return MSG_HTML_RESOURCES_UNAVAILABLE;
        }
        if (err.contains("HTML 引用的脚本或样式无法访问") || err.contains("请确认已一并提供")) {
            return MSG_HTML_RESOURCES_UNAVAILABLE;
        }

        if (looksLikeStackOrJvmDetail(err)) {
            return null;
        }
        if (!containsCjk(err)) {
            return null;
        }
        return err;
    }

    private static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeStackOrJvmDetail(String err) {
        if (err.length() > 2000) {
            return true;
        }
        return err.contains("\tat ") || err.contains("\n\tat ")
                || err.contains("Caused by:") || err.contains(".java:")
                || err.contains("\nCaused by");
    }
}
