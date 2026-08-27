package cn.keking.utils;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.NotOLE2FileException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Office工具类（OOXML 加密检测 / 密码校验，兼容 docx、xlsx、pptx 等）
 *
 * @author ylyue
 * @since 2022/7/5
 */
public class OfficeUtils {

    private static final Logger logger = LoggerFactory.getLogger(OfficeUtils.class);
    private static final String POI_INVALID_PASSWORD_MSG = "password";

    /**
     * 判断 office（word,excel,ppt）文件是否受密码保护
     */
    public static boolean isPwdProtected(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        try {
            if (hasOoxmlEncryptionZipMarkers(path)) {
                return true;
            }
            if (lower.endsWith(".docx") || lower.endsWith(".docm")) {
                return probeDocxNeedsPassword(path);
            }
            if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm")) {
                return probeExcelOoxmlNeedsPassword(path);
            }
            if (lower.endsWith(".pptx") || lower.endsWith(".pptm")) {
                return probePptxNeedsPassword(path);
            }
        } catch (Exception e) {
            logger.debug("isPwdProtected OOXML 探测异常: {}", path, e);
        }
        return isPwdProtectedLegacy(path);
    }

    /**
     * 部分受保护 OOXML 在 zip 根目录含加密元数据（与是否 OLE 封装无关）
     */
    private static boolean hasOoxmlEncryptionZipMarkers(String path) {
        try (ZipFile zf = new ZipFile(path)) {
            return zf.getEntry("EncryptedPackage") != null
                    || zf.getEntry("EncryptionInfo") != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean probeDocxNeedsPassword(String path) {
        try (InputStream in = Files.newInputStream(Paths.get(path));
             XWPFDocument ignored = new XWPFDocument(in)) {
            return false;
        } catch (EncryptedDocumentException e) {
            return true;
        } catch (IOException e) {
            return false;
        } catch (Exception e) {
            String m = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
            return m.contains("encrypt") || m.contains("password") || m.contains("protected");
        }
    }

    private static boolean probeExcelOoxmlNeedsPassword(String path) {
        try (InputStream in = Files.newInputStream(Paths.get(path));
             Workbook ignored = WorkbookFactory.create(in)) {
            return false;
        } catch (EncryptedDocumentException e) {
            return true;
        } catch (IOException e) {
            return false;
        } catch (Exception e) {
            String m = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
            return m.contains("encrypt") || m.contains("password") || m.contains("protected");
        }
    }

    private static boolean probePptxNeedsPassword(String path) {
        try (OPCPackage pkg = OPCPackage.open(Paths.get(path).toFile());
             XMLSlideShow ignored = new XMLSlideShow(pkg)) {
            return false;
        } catch (EncryptedDocumentException e) {
            return true;
        } catch (IOException e) {
            return false;
        } catch (Exception e) {
            String m = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
            return m.contains("encrypt") || m.contains("password") || m.contains("protected");
        }
    }

    /**
     * 旧版二进制/Freemarker 抽取路径（.doc/.xls/.ppt 等）
     */
    private static boolean isPwdProtectedLegacy(String path) {
        InputStream propStream = null;
        try {
            propStream = Files.newInputStream(Paths.get(path));
            ExtractorFactory.createExtractor(propStream);
        } catch (IOException | EncryptedDocumentException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains(POI_INVALID_PASSWORD_MSG)) {
                return true;
            }
        } catch (Exception e) {
            Throwable[] throwableArray = ExceptionUtils.getThrowables(e);
            for (Throwable throwable : throwableArray) {
                if (throwable instanceof IOException || throwable instanceof EncryptedDocumentException) {
                    if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains(POI_INVALID_PASSWORD_MSG)) {
                        return true;
                    }
                }
            }
        } finally {
            if (propStream != null) {
                try {
                    propStream.close();
                } catch (IOException e) {
                    logger.error("Failed to close input stream for file: {}", path, e);
                }
            }
        }
        return false;
    }

    /**
     * 判断 office 文件是否可用给定密码打开（OOXML 与旧版分别处理）
     */
    public static synchronized boolean isCompatible(String path, String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm")) {
            return isCompatibleExcelOoxml(path, password);
        }
        if (lower.endsWith(".docx") || lower.endsWith(".docm")) {
            return isCompatibleWordOoxml(path, password);
        }
        if (lower.endsWith(".pptx") || lower.endsWith(".pptm")) {
            return isCompatiblePptx(path, password);
        }
        return isCompatibleLegacy(path, password);
    }

    private static boolean isCompatibleExcelOoxml(String path, String password) {
        try (Workbook ignored = WorkbookFactory.create(new File(path), password, true)) {
            return true;
        } catch (Exception e) {
            logger.debug("Excel 密码校验失败: {}", path, e);
            return false;
        }
    }

    /**
     * Word OOXML：先尝试 OLE 封装的标准加密；否则交给 OPC + XWPF 解密路径
     */
    private static boolean isCompatibleWordOoxml(String path, String password) {
        try (FileInputStream fis = new FileInputStream(path);
             POIFSFileSystem fs = new POIFSFileSystem(fis)) {
            EncryptionInfo info = new EncryptionInfo(fs);
            Decryptor d = Decryptor.getInstance(info);
            if (!d.verifyPassword(password)) {
                return false;
            }
            try (InputStream is = d.getDataStream(fs);
                 OPCPackage pkg = OPCPackage.open(is);
                 XWPFDocument doc = new XWPFDocument(pkg)) {
                return true;
            }
        } catch (NotOLE2FileException e) {
            return isCompatibleWordOpcPlain(path, password);
        } catch (Exception e) {
            logger.debug("Word OLE 加密校验失败，尝试 OPC: {}", path, e);
            return isCompatibleWordOpcPlain(path, password);
        }
    }

    /**
     * 标准 zip 式 docx：若无需密码即可打开则校验通过；若抛 EncryptedDocumentException（敏捷加密等），
     * POI 难以在无专用解密流的情况下校验密码，交由 LibreOffice 的 loadProperties.Password 验证。
     */
    private static boolean isCompatibleWordOpcPlain(String path, String password) {
        try (InputStream in = Files.newInputStream(Paths.get(path));
             XWPFDocument ignored = new XWPFDocument(in)) {
            return true;
        } catch (EncryptedDocumentException e) {
            return true;
        } catch (Exception e) {
            logger.debug("Word OPC 校验: {}", path, e);
            return false;
        }
    }

    private static boolean isCompatiblePptx(String path, String password) {
        try (FileInputStream fis = new FileInputStream(path);
             POIFSFileSystem fs = new POIFSFileSystem(fis)) {
            EncryptionInfo info = new EncryptionInfo(fs);
            Decryptor d = Decryptor.getInstance(info);
            if (!d.verifyPassword(password)) {
                return false;
            }
            try (InputStream is = d.getDataStream(fs);
                 OPCPackage pkg = OPCPackage.open(is);
                 XMLSlideShow ignored = new XMLSlideShow(pkg)) {
                return true;
            }
        } catch (NotOLE2FileException e) {
            try (OPCPackage pkg = OPCPackage.open(Paths.get(path).toFile());
                 XMLSlideShow ignored = new XMLSlideShow(pkg)) {
                return true;
            } catch (EncryptedDocumentException ex) {
                return true;
            } catch (Exception ex) {
                return false;
            }
        } catch (Exception e) {
            logger.debug("pptx 密码校验失败: {}", path, e);
            return false;
        }
    }

    private static boolean isCompatibleLegacy(String path, String password) {
        InputStream propStream = null;
        try {
            propStream = Files.newInputStream(Paths.get(path));
            Biff8EncryptionKey.setCurrentUserPassword(password);
            ExtractorFactory.createExtractor(propStream);
        } catch (Exception e) {
            return false;
        } finally {
            Biff8EncryptionKey.setCurrentUserPassword(null);
            if (propStream != null) {
                try {
                    propStream.close();
                } catch (IOException e) {
                    logger.error("Failed to close input stream for file: {}", path, e);
                }
            }
        }
        return true;
    }
}
