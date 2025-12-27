package com.monitor.backend.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 本地文件缓存策略
 * <p>
 * 适用于单机部署、无中间件依赖的场景。
 * 支持GZIP压缩、分块读写、自动过期清理。
 * </p>
 */
@Component
public class FileCacheStrategy implements CacheStrategy {

    private static final Logger log = LoggerFactory.getLogger(FileCacheStrategy.class);
    private static final String DATA_FILE_SUFFIX = ".json";
    private static final String COMPRESSED_SUFFIX = ".gz";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${monitor.cache.file.base-path:/tmp/busi-monitor/cache}")
    private String basePath;

    @Value("${monitor.cache.file.compress:true}")
    private boolean compress;

    @Value("${monitor.cache.file.ttl-hours:24}")
    private int ttlHours;

    @Override
    public Type getType() {
        return Type.FILE;
    }

    @Override
    public void write(String cacheKey, List<Map<String, Object>> data) {
        try {
            Path dir = Paths.get(basePath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            Path filePath = getFilePath(cacheKey);
            String json = objectMapper.writeValueAsString(data);

            if (compress) {
                try (GZIPOutputStream gzOut = new GZIPOutputStream(
                        new FileOutputStream(filePath.toFile()))) {
                    gzOut.write(json.getBytes("UTF-8"));
                }
            } else {
                Files.writeString(filePath, json);
            }

            log.info("文件缓存写入成功: key={}, count={}, path={}", 
                    cacheKey, data.size(), filePath);
        } catch (Exception e) {
            log.error("文件缓存写入失败: key={}", cacheKey, e);
            throw new RuntimeException("文件缓存写入失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> read(String cacheKey) {
        try {
            Path filePath = getFilePath(cacheKey);
            if (!Files.exists(filePath)) {
                return Collections.emptyList();
            }

            String json;
            if (compress) {
                try (GZIPInputStream gzIn = new GZIPInputStream(
                        new FileInputStream(filePath.toFile()));
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = gzIn.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    json = baos.toString("UTF-8");
                }
            } else {
                json = Files.readString(filePath);
            }

            return objectMapper.readValue(json, 
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("文件缓存读取失败: key={}", cacheKey, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> readBatch(String cacheKey, int offset, int limit) {
        List<Map<String, Object>> all = read(cacheKey);
        if (all.isEmpty() || offset >= all.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(offset + limit, all.size());
        return all.subList(offset, end);
    }

    @Override
    public long count(String cacheKey) {
        return read(cacheKey).size();
    }

    @Override
    public void clear(String cacheKey) {
        try {
            Path filePath = getFilePath(cacheKey);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("文件缓存清理成功: key={}", cacheKey);
            }
        } catch (Exception e) {
            log.error("文件缓存清理失败: key={}", cacheKey, e);
        }
    }

    @Override
    public boolean exists(String cacheKey) {
        return Files.exists(getFilePath(cacheKey));
    }

    /**
     * 清理过期缓存文件
     */
    public void cleanExpired() {
        try {
            Path dir = Paths.get(basePath);
            if (!Files.exists(dir)) {
                return;
            }

            long expirationTime = System.currentTimeMillis() - (ttlHours * 3600 * 1000L);
            Files.list(dir)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() < expirationTime;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            log.info("清理过期缓存文件: {}", p);
                        } catch (IOException e) {
                            log.warn("删除过期文件失败: {}", p);
                        }
                    });
        } catch (Exception e) {
            log.error("清理过期缓存失败", e);
        }
    }

    private Path getFilePath(String cacheKey) {
        String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_-]", "_") + DATA_FILE_SUFFIX;
        if (compress) {
            fileName += COMPRESSED_SUFFIX;
        }
        return Paths.get(basePath, fileName);
    }
}
