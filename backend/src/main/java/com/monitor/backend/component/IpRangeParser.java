package com.monitor.backend.component;

import com.monitor.backend.constant.BatchDefaults;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * IP段解析服务
 * 支持多种格式：单IP、逗号分隔、范围(1-10)、CIDR
 */
@Component
public class IpRangeParser {
    
    /**
     * 解析IP输入，返回IP列表
     * 支持格式：
     * - 单IP: 192.168.1.1
     * - 逗号分隔: 192.168.1.1,192.168.1.2
     * - 范围: 192.168.1.1-10
     * - CIDR: 192.168.1.0/24
     */
    public List<String> parse(String input) {
        List<String> result = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) {
            return result;
        }
        
        // 分割逗号
        String[] parts = input.split(BatchDefaults.DEFAULT_SEPARATOR);
        for (String part : parts) {
            part = part.trim();
            if (part.contains("/")) {
                // CIDR格式
                result.addAll(parseCidr(part));
            } else if (part.contains("-")) {
                // 范围格式
                result.addAll(parseRange(part));
            } else {
                // 单IP
                result.add(part);
            }
        }
        
        return result;
    }
    
    /**
     * 解析范围格式: 192.168.1.1-10 或 192.168.1.1-192.168.1.10
     */
    public List<String> parseRange(String rangeStr) {
        List<String> result = new ArrayList<>();
        
        int dashIdx = rangeStr.lastIndexOf("-");
        if (dashIdx == -1) {
            result.add(rangeStr);
            return result;
        }
        
        String start = rangeStr.substring(0, dashIdx);
        String end = rangeStr.substring(dashIdx + 1);
        
        // 判断是完整IP还是只有最后一段
        if (end.contains(".")) {
            // 完整IP: 192.168.1.1-192.168.1.10
            int startNum = getLastOctet(start);
            int endNum = getLastOctet(end);
            String prefix = start.substring(0, start.lastIndexOf(".") + 1);
            
            for (int i = startNum; i <= endNum; i++) {
                result.add(prefix + i);
            }
        } else {
            // 只有最后一段: 192.168.1.1-10
            int startNum = getLastOctet(start);
            int endNum = Integer.parseInt(end);
            String prefix = start.substring(0, start.lastIndexOf(".") + 1);
            
            for (int i = startNum; i <= endNum; i++) {
                result.add(prefix + i);
            }
        }
        
        return result;
    }
    
    /**
     * 解析CIDR格式: 192.168.1.0/24
     */
    public List<String> parseCidr(String cidr) {
        List<String> result = new ArrayList<>();
        
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            result.add(cidr);
            return result;
        }
        
        String ip = parts[0];
        int prefix = Integer.parseInt(parts[1]);
        
        // 简化处理：只支持 /24 和 /16
        String[] octets = ip.split("\\.");
        if (prefix == 24) {
            // 192.168.1.0/24 -> 192.168.1.1 - 192.168.1.254
            String base = octets[0] + "." + octets[1] + "." + octets[2] + ".";
            for (int i = 1; i <= 254; i++) {
                result.add(base + i);
            }
        } else if (prefix == 16) {
            // 192.168.0.0/16 -> 只生成前100个作为示例
            String base = octets[0] + "." + octets[1] + ".";
            for (int j = 0; j <= 1; j++) {
                for (int i = 1; i <= 50; i++) {
                    result.add(base + j + "." + i);
                }
            }
        } else {
            // 不支持的前缀，返回原始IP
            result.add(ip);
        }
        
        return result;
    }
    
    private int getLastOctet(String ip) {
        String[] parts = ip.split("\\.");
        return Integer.parseInt(parts[parts.length - 1]);
    }
}
