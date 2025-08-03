package com.all.in.one.agent.storage.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 文件安全工具类
 */
@Component
public class FileSecurityUtils {

    // 允许的文件类型
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", // 图片
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", // 文档
        "txt", "md", "json", "xml", "yaml", "yml", // 文本
        "zip", "rar", "7z", "tar", "gz" // 压缩包
    );

    // 危险文件类型
    private static final List<String> DANGEROUS_EXTENSIONS = Arrays.asList(
        "exe", "bat", "cmd", "com", "pif", "scr", "jar", "js", "vbs", "sh"
    );

    // 最大文件大小 (100MB)
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    /**
     * 验证文件是否安全
     */
    public boolean isFileSecure(String filename, long fileSize) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }

        // 检查文件大小
        if (fileSize > MAX_FILE_SIZE) {
            return false;
        }

        // 检查文件扩展名
        String extension = getFileExtension(filename).toLowerCase();

        // 检查是否为危险文件类型
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            return false;
        }

        // 检查是否为允许的文件类型
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 清理文件名，防止路径遍历攻击
     */
    public String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "unnamed_file";
        }

        // 移除路径分隔符和特殊字符
        return filename.replaceAll("[/\\\\:*?\"<>|]", "_")
                      .replaceAll("\\.\\.", "_");
    }
}
