package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.FileType;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FileHandlerService;
import cn.keking.utils.DownloadUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimTextFilePreviewImplTests {

    private static final String CONTENT = "**新浪集团反舞弊举报范围**\r\n"
            + "1. <span style=\"color: ;\">员工</span>**侵占、挪用**\r\n"
            + "2. &lt;literal-tag&gt; &amp; original entities\r\n";

    @TempDir
    Path tempDir;

    private Boolean originalCacheEnabled;

    @BeforeEach
    void enablePreviewCache() {
        originalCacheEnabled = ConfigConstants.isCacheEnabled();
        ConfigConstants.setCacheEnabledValueValue(true);
    }

    @AfterEach
    void restorePreviewCache() {
        ConfigConstants.setCacheEnabledValueValue(originalCacheEnabled);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void markdownModelPreservesHtmlAndExistingEntities(boolean cached) throws Exception {
        assertEquals(CONTENT, previewText(FileType.MARKDOWN, cached, false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void plainTextModelStillEscapesHtml(boolean cached) throws Exception {
        assertEquals(HtmlUtils.htmlEscape(CONTENT), previewText(FileType.SIMTEXT, cached, false));
    }

    @Test
    void forcedMarkdownRefreshAlsoPreservesOriginalSource() throws Exception {
        assertEquals(CONTENT, previewText(FileType.MARKDOWN, true, true));
    }

    private String previewText(FileType type, boolean cached, boolean refresh) throws Exception {
        String name = type == FileType.MARKDOWN ? "source.md" : "source.txt";
        Path source = Files.writeString(tempDir.resolve(name), CONTENT, StandardCharsets.UTF_8);
        FileAttribute attribute = new FileAttribute(type, type == FileType.MARKDOWN ? "md" : "txt",
                name, "https://example.test/" + name);
        attribute.setForceUpdatedCache(refresh);
        FileHandlerService handler = mock(FileHandlerService.class);
        when(handler.listConvertedFiles()).thenReturn(cached ? Map.of(name, source.toString()) : Map.of());
        when(handler.getConvertedFile(name)).thenReturn(source.toString());
        SimTextFilePreviewImpl preview = new SimTextFilePreviewImpl(handler, mock(OtherFilePreviewImpl.class));
        ExtendedModelMap model = new ExtendedModelMap();

        try (MockedStatic<DownloadUtils> downloads = mockStatic(DownloadUtils.class)) {
            downloads.when(() -> DownloadUtils.downLoad(attribute, name))
                    .thenReturn(new ReturnResponse<>(0, "OK", source.toString()));
            if (type == FileType.MARKDOWN) {
                assertEquals("markdown", new MarkdownFilePreviewImpl(preview)
                        .filePreviewHandle(attribute.getUrl(), model, attribute));
            } else {
                assertEquals("txt", preview.filePreviewHandle(attribute.getUrl(), model, attribute));
            }
            if (cached && !refresh) {
                downloads.verifyNoInteractions();
            } else {
                downloads.verify(() -> DownloadUtils.downLoad(attribute, name));
                verify(handler).addConvertedFile(name, source.toString());
            }
        }
        return new String(Base64.getDecoder().decode((String) model.get("textData")), StandardCharsets.UTF_8);
    }
}
