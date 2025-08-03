package com.all.in.one.agent.storage.service.impl;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 文件预览服务实现类
 */
@Service
public class FilePreviewService {

    // 可直接预览的图片类型
    private static final List<String> IMAGE_TYPES = Arrays.asList(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    );

    // 可预览的文本类型
    private static final List<String> TEXT_TYPES = Arrays.asList(
        "txt", "md", "json", "xml", "yaml", "yml", "log", "properties"
    );

    // 可预览的文档类型
    private static final List<String> DOCUMENT_TYPES = Arrays.asList(
        "pdf"
    );

    // 可预览的视频类型
    private static final List<String> VIDEO_TYPES = Arrays.asList(
        "mp4", "webm", "ogg"
    );

    // 可预览的音频类型
    private static final List<String> AUDIO_TYPES = Arrays.asList(
        "mp3", "wav", "ogg", "m4a"
    );

    /**
     * 判断文件是否可以预览
     */
    public boolean canPreview(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return IMAGE_TYPES.contains(extension) ||
               TEXT_TYPES.contains(extension) ||
               DOCUMENT_TYPES.contains(extension) ||
               VIDEO_TYPES.contains(extension) ||
               AUDIO_TYPES.contains(extension);
    }

    /**
     * 获取文件预览类型
     */
    public String getPreviewType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();

        if (IMAGE_TYPES.contains(extension)) {
            return "image";
        } else if (TEXT_TYPES.contains(extension)) {
            return "text";
        } else if (DOCUMENT_TYPES.contains(extension)) {
            return "document";
        } else if (VIDEO_TYPES.contains(extension)) {
            return "video";
        } else if (AUDIO_TYPES.contains(extension)) {
            return "audio";
        }

        return "unknown";
    }

    /**
     * 获取文件图标类名
     */
    public String getFileIcon(String filename) {
        String extension = getFileExtension(filename).toLowerCase();

        if (IMAGE_TYPES.contains(extension)) {
            return "bi-file-image";
        } else if (TEXT_TYPES.contains(extension)) {
            return "bi-file-text";
        } else if (DOCUMENT_TYPES.contains(extension)) {
            return "bi-file-pdf";
        } else if (VIDEO_TYPES.contains(extension)) {
            return "bi-file-play";
        } else if (AUDIO_TYPES.contains(extension)) {
            return "bi-file-music";
        } else if (Arrays.asList("zip", "rar", "7z", "tar", "gz").contains(extension)) {
            return "bi-file-zip";
        } else if (Arrays.asList("doc", "docx").contains(extension)) {
            return "bi-file-word";
        } else if (Arrays.asList("xls", "xlsx").contains(extension)) {
            return "bi-file-excel";
        } else if (Arrays.asList("ppt", "pptx").contains(extension)) {
            return "bi-file-ppt";
        }

        return "bi-file-earmark";
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
