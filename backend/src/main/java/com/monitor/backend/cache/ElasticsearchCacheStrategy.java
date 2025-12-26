package com.monitor.backend.cache;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Elasticsearch缓存策略
 * 适用场景：海量数据（>1000万）、需要全文搜索、复杂聚合查询
 * 优势：高扩展性、支持复杂查询、水平扩展
 * 劣势：写入延迟较高、需要维护ES集群
 */
@Component
public class ElasticsearchCacheStrategy implements CacheStrategy {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchCacheStrategy.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    @Value("${monitor.cache.es.hosts:http://localhost:9200}")
    private String esHosts;

    @Value("${monitor.cache.es.index-prefix:busi-monitor-cache-}")
    private String indexPrefix;

    @Value("${monitor.cache.es.username:}")
    private String username;

    @Value("${monitor.cache.es.password:}")
    private String password;

    @Value("${monitor.cache.es.batch-size:1000}")
    private int batchSize;

    public ElasticsearchCacheStrategy() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Type getType() {
        return Type.ES;
    }

    @Override
    public void write(String cacheKey, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        String indexName = getIndexName(cacheKey);

        try {
            // 1. 删除旧索引（如果存在）
            deleteIndex(indexName);

            // 2. 创建新索引
            createIndex(indexName, data.get(0).keySet());

            // 3. 批量写入数据
            bulkWrite(indexName, data);

            log.info("ES缓存写入完成，索引: {}，记录数: {}", indexName, data.size());
        } catch (Exception e) {
            log.error("ES缓存写入失败: {}", e.getMessage(), e);
            throw new RuntimeException("ES缓存写入失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> read(String cacheKey) {
        String indexName = getIndexName(cacheKey);
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            // 使用scroll API读取大量数据
            String scrollId = null;
            int scrollSize = 10000;

            // 初始化scroll
            String scrollUri = esHosts + "/" + indexName + "/_search?scroll=5m";
            String queryBody = "{\"size\":" + scrollSize + ",\"query\":{\"match_all\":{}}}";

            HttpResponse<String> initResponse = executeRequest("POST", scrollUri, queryBody);
            if (initResponse.statusCode() >= 400) {
                log.warn("ES读取失败，索引不存在: {}", indexName);
                return result;
            }

            Map<String, Object> response = objectMapper.readValue(initResponse.body(), Map.class);
            scrollId = (String) response.get("_scroll_id");
            List<Map<String, Object>> hits = extractHits(response);
            result.addAll(hits);

            // 继续scroll直到没有更多数据
            while (!hits.isEmpty()) {
                String scrollBody = "{\"scroll\":\"5m\",\"scroll_id\":\"" + scrollId + "\"}";
                HttpResponse<String> scrollResponse = executeRequest("POST", esHosts + "/_search/scroll", scrollBody);
                response = objectMapper.readValue(scrollResponse.body(), Map.class);
                scrollId = (String) response.get("_scroll_id");
                hits = extractHits(response);
                result.addAll(hits);
            }

            // 清理scroll上下文
            if (scrollId != null) {
                executeRequest("DELETE", esHosts + "/_search/scroll", "{\"scroll_id\":\"" + scrollId + "\"}");
            }

            log.info("ES缓存读取完成，索引: {}，记录数: {}", indexName, result.size());
        } catch (Exception e) {
            log.error("ES缓存读取失败: {}", e.getMessage(), e);
        }

        return result;
    }

    @Override
    public void clear(String cacheKey) {
        String indexName = getIndexName(cacheKey);
        try {
            deleteIndex(indexName);
            log.info("ES缓存清除成功: {}", indexName);
        } catch (Exception e) {
            log.error("ES缓存清除失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String cacheKey) {
        String indexName = getIndexName(cacheKey);
        try {
            HttpResponse<String> response = executeRequest("HEAD", esHosts + "/" + indexName, null);
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long count(String cacheKey) {
        String indexName = getIndexName(cacheKey);
        try {
            HttpResponse<String> response = executeRequest("GET", esHosts + "/" + indexName + "/_count", null);
            if (response.statusCode() >= 400) {
                return 0;
            }
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            return ((Number) result.get("count")).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public List<Map<String, Object>> readBatch(String cacheKey, int offset, int limit) {
        String indexName = getIndexName(cacheKey);
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            String uri = esHosts + "/" + indexName + "/_search";
            String queryBody = "{\"from\":" + offset + ",\"size\":" + limit + ",\"query\":{\"match_all\":{}}}";
            HttpResponse<String> response = executeRequest("POST", uri, queryBody);
            if (response.statusCode() >= 400) {
                log.warn("ES分批读取失败，索引: {}", indexName);
                return result;
            }
            Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
            result = extractHits(responseMap);
            log.debug("ES分批读取完成，索引: {}，offset: {}，limit: {}，实际读取: {}", indexName, offset, limit, result.size());
        } catch (Exception e) {
            log.error("ES分批读取失败: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 执行聚合查询
     */
    public Map<String, Object> aggregate(String cacheKey, String aggregationDsl) {
        String indexName = getIndexName(cacheKey);
        try {
            String uri = esHosts + "/" + indexName + "/_search";
            HttpResponse<String> response = executeRequest("POST", uri, aggregationDsl);
            return objectMapper.readValue(response.body(), Map.class);
        } catch (Exception e) {
            log.error("ES聚合查询失败: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 执行条件查询
     */
    public List<Map<String, Object>> search(String cacheKey, String queryDsl) {
        String indexName = getIndexName(cacheKey);
        try {
            String uri = esHosts + "/" + indexName + "/_search";
            HttpResponse<String> response = executeRequest("POST", uri, queryDsl);
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            return extractHits(result);
        } catch (Exception e) {
            log.error("ES条件查询失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private String getIndexName(String cacheKey) {
        return (indexPrefix + cacheKey).toLowerCase().replaceAll("[^a-z0-9-_]", "_");
    }

    private void deleteIndex(String indexName) throws IOException, InterruptedException {
        executeRequest("DELETE", esHosts + "/" + indexName, null);
    }

    private void createIndex(String indexName, Set<String> fields) throws IOException, InterruptedException {
        // 构建映射
        Map<String, Object> properties = new HashMap<>();
        for (String field : fields) {
            Map<String, Object> fieldMapping = new HashMap<>();
            fieldMapping.put("type", "keyword"); // 默认使用keyword类型
            properties.put(field, fieldMapping);
        }

        Map<String, Object> mappings = new HashMap<>();
        mappings.put("properties", properties);

        Map<String, Object> settings = new HashMap<>();
        settings.put("number_of_shards", 3);
        settings.put("number_of_replicas", 1);
        settings.put("refresh_interval", "1s");

        Map<String, Object> indexBody = new HashMap<>();
        indexBody.put("settings", settings);
        indexBody.put("mappings", mappings);

        String body = objectMapper.writeValueAsString(indexBody);
        executeRequest("PUT", esHosts + "/" + indexName, body);
    }

    private void bulkWrite(String indexName, List<Map<String, Object>> data) throws IOException, InterruptedException {
        StringBuilder bulk = new StringBuilder();
        int count = 0;

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> doc = data.get(i);

            // 索引操作行
            bulk.append("{\"index\":{\"_index\":\"").append(indexName).append("\"}}\n");
            // 文档内容行
            bulk.append(objectMapper.writeValueAsString(doc)).append("\n");
            count++;

            // 达到批量大小时执行写入
            if (count >= batchSize) {
                executeBulk(bulk.toString());
                bulk = new StringBuilder();
                count = 0;
            }
        }

        // 处理剩余数据
        if (count > 0) {
            executeBulk(bulk.toString());
        }

        // 强制刷新，确保数据可查
        executeRequest("POST", esHosts + "/" + indexName + "/_refresh", null);
    }

    private void executeBulk(String bulkBody) throws IOException, InterruptedException {
        HttpResponse<String> response = executeRequest("POST", esHosts + "/_bulk", bulkBody);
        if (response.statusCode() >= 400) {
            log.error("ES bulk写入失败: {}", response.body());
            throw new RuntimeException("ES bulk写入失败");
        }
    }

    private HttpResponse<String> executeRequest(String method, String uri, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60));

        // 添加认证
        if (username != null && !username.isEmpty()) {
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            builder.header("Authorization", "Basic " + auth);
        }

        // 设置请求方法
        switch (method.toUpperCase()) {
            case "GET":
                builder.GET();
                break;
            case "POST":
                builder.POST(
                        body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
                break;
            case "PUT":
                builder.PUT(
                        body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
                break;
            case "DELETE":
                builder.DELETE();
                break;
            case "HEAD":
                builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                break;
            default:
                throw new IllegalArgumentException("不支持的HTTP方法: " + method);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractHits(Map<String, Object> response) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> hits = (Map<String, Object>) response.get("hits");
        if (hits != null) {
            List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
            if (hitList != null) {
                for (Map<String, Object> hit : hitList) {
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    if (source != null) {
                        result.add(source);
                    }
                }
            }
        }
        return result;
    }
}
