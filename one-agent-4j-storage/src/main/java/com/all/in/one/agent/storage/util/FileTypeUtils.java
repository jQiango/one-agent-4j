package com.all.in.one.agent.storage.util;

import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件类型工具类
 */
public class FileTypeUtils {
    
    // 文件扩展名到MIME类型的映射
    private static final Map<String, String> EXTENSION_TO_MIME_TYPE = new HashMap<>();
    
    static {
        // 图片类型
        EXTENSION_TO_MIME_TYPE.put("jpg", "image/jpeg");
        EXTENSION_TO_MIME_TYPE.put("jpeg", "image/jpeg");
        EXTENSION_TO_MIME_TYPE.put("png", "image/png");
        EXTENSION_TO_MIME_TYPE.put("gif", "image/gif");
        EXTENSION_TO_MIME_TYPE.put("bmp", "image/bmp");
        EXTENSION_TO_MIME_TYPE.put("webp", "image/webp");
        EXTENSION_TO_MIME_TYPE.put("svg", "image/svg+xml");
        EXTENSION_TO_MIME_TYPE.put("ico", "image/x-icon");
        
        // 文档类型
        EXTENSION_TO_MIME_TYPE.put("pdf", "application/pdf");
        EXTENSION_TO_MIME_TYPE.put("doc", "application/msword");
        EXTENSION_TO_MIME_TYPE.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        EXTENSION_TO_MIME_TYPE.put("xls", "application/vnd.ms-excel");
        EXTENSION_TO_MIME_TYPE.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        EXTENSION_TO_MIME_TYPE.put("ppt", "application/vnd.ms-powerpoint");
        EXTENSION_TO_MIME_TYPE.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        
        // 文本类型
        EXTENSION_TO_MIME_TYPE.put("txt", "text/plain");
        EXTENSION_TO_MIME_TYPE.put("md", "text/markdown");
        EXTENSION_TO_MIME_TYPE.put("json", "application/json");
        EXTENSION_TO_MIME_TYPE.put("xml", "application/xml");
        EXTENSION_TO_MIME_TYPE.put("yaml", "application/x-yaml");
        EXTENSION_TO_MIME_TYPE.put("yml", "application/x-yaml");
        EXTENSION_TO_MIME_TYPE.put("html", "text/html");
        EXTENSION_TO_MIME_TYPE.put("css", "text/css");
        EXTENSION_TO_MIME_TYPE.put("js", "application/javascript");
        
        // 压缩包类型
        EXTENSION_TO_MIME_TYPE.put("zip", "application/zip");
        EXTENSION_TO_MIME_TYPE.put("rar", "application/x-rar-compressed");
        EXTENSION_TO_MIME_TYPE.put("7z", "application/x-7z-compressed");
        EXTENSION_TO_MIME_TYPE.put("tar", "application/x-tar");
        EXTENSION_TO_MIME_TYPE.put("gz", "application/gzip");
        
        // 音频类型
        EXTENSION_TO_MIME_TYPE.put("mp3", "audio/mpeg");
        EXTENSION_TO_MIME_TYPE.put("wav", "audio/wav");
        EXTENSION_TO_MIME_TYPE.put("ogg", "audio/ogg");
        EXTENSION_TO_MIME_TYPE.put("flac", "audio/flac");
        
        // 视频类型
        EXTENSION_TO_MIME_TYPE.put("mp4", "video/mp4");
        EXTENSION_TO_MIME_TYPE.put("avi", "video/x-msvideo");
        EXTENSION_TO_MIME_TYPE.put("mov", "video/quicktime");
        EXTENSION_TO_MIME_TYPE.put("wmv", "video/x-ms-wmv");
        EXTENSION_TO_MIME_TYPE.put("flv", "video/x-flv");
        EXTENSION_TO_MIME_TYPE.put("webm", "video/webm");
    }
    
    /**
     * 根据文件名获取MIME类型
     */
    public static String getMimeType(String filename) {
        if (filename == null || filename.isEmpty()) {
            return MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        }
        
        String extension = getFileExtension(filename).toLowerCase();
        return EXTENSION_TO_MIME_TYPE.getOrDefault(extension, MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);
    }
    
    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        
        return filename.substring(lastDotIndex + 1);
    }
    
    /**
     * 检查是否为图片文件
     */
    public static boolean isImage(String filename) {
        String mimeType = getMimeType(filename);
        return mimeType.startsWith("image/");
    }
    
    /**
     * 检查是否为视频文件
     */
    public static boolean isVideo(String filename) {
        String mimeType = getMimeType(filename);
        return mimeType.startsWith("video/");
    }
    
    /**
     * 检查是否为音频文件
     */
    public static boolean isAudio(String filename) {
        String mimeType = getMimeType(filename);
        return mimeType.startsWith("audio/");
    }
    
    /**
     * 检查是否为文档文件
     */
    public static boolean isDocument(String filename) {
        String mimeType = getMimeType(filename);
        return mimeType.equals("application/pdf") || 
               mimeType.contains("word") || 
               mimeType.contains("excel") || 
               mimeType.contains("powerpoint");
    }
    
    /**
     * 检查是否为文本文件
     */
    public static boolean isText(String filename) {
        String mimeType = getMimeType(filename);
        return mimeType.startsWith("text/") || 
               mimeType.equals("application/json") || 
               mimeType.equals("application/xml") || 
               mimeType.equals("application/x-yaml");
    }
    
    /**
     * 检查是否为压缩文件
     */
    public static boolean isArchive(String filename) {
        String mimeType = getMimeType(filename);
        return mimeType.equals("application/zip") || 
               mimeType.equals("application/x-rar-compressed") || 
               mimeType.equals("application/x-7z-compressed") || 
               mimeType.equals("application/x-tar") || 
               mimeType.equals("application/gzip");
    }
} 