package com.monitor.backend.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.monitor.backend.entity.ServerAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * SSH 执行服务
 * 支持SSH远程执行脚本（使用JSch库，支持密码和密钥认证）
 */
@Service
public class SshExecutorService {
    
    private static final Logger log = LoggerFactory.getLogger(SshExecutorService.class);
    
    private final EncryptionService encryptionService;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    
    public SshExecutorService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }
    
    /**
     * 创建SSH会话（支持密码和密钥认证）
     */
    private Session createSession(ServerAsset server) throws JSchException {
        JSch jsch = new JSch();
        
        String ip = server.getIp();
        int port = server.getPort() != null ? server.getPort() : 22;
        String username = server.getUsername();
        
        log.debug("Creating SSH session to {}@{}:{}", username, ip, port);
        
        Session session = jsch.getSession(username, ip, port);
        
        // 尝试使用密码认证 - 从 passwordEncrypted 字段读取加密密码
        String encryptedPassword = server.getPasswordEncrypted();
        if (encryptedPassword != null && !encryptedPassword.isEmpty()) {
            try {
                String decryptedPassword = encryptionService.decrypt(encryptedPassword);
                session.setPassword(decryptedPassword);
                log.debug("Using password authentication (decrypted from passwordEncrypted)");
            } catch (Exception e) {
                log.error("Failed to decrypt password for {}@{}: {}", username, ip, e.getMessage());
                // 解密失败，抛出异常而不是静默失败
                throw new JSchException("密码解密失败，请检查加密密钥配置: " + e.getMessage());
            }
        } else {
            log.debug("No password provided, will try key-based authentication");
        }
        
        // SSH配置
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password,publickey,keyboard-interactive");
        session.setTimeout(10000); // 10秒连接超时
        
        session.connect();
        log.info("SSH session connected to {}@{}", username, ip);
        
        return session;
    }
    
    /**
     * 执行远程命令
     */
    private String executeRemoteCommand(Session session, String command) throws JSchException, IOException {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            // 获取命令输出流
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            
            channel.setOutputStream(outputStream);
            channel.setErrStream(errorStream);
            
            channel.connect();
            
            // 等待命令执行完成（最多30秒）
            int maxWait = 30000;
            int waited = 0;
            while (!channel.isClosed() && waited < maxWait) {
                try {
                    Thread.sleep(100);
                    waited += 100;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Command execution interrupted", e);
                }
            }
            
            if (!channel.isClosed()) {
                throw new IOException("Command execution timeout");
            }
            
            int exitStatus = channel.getExitStatus();
            String output = outputStream.toString("UTF-8");
            String error = errorStream.toString("UTF-8");
            
            if (exitStatus != 0 && !error.isEmpty()) {
                log.warn("Command executed with non-zero exit code {}: {}", exitStatus, error);
                throw new IOException("Command failed with exit code " + exitStatus + ": " + error);
            }
            
            return output.trim();
            
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
    
    /**
     * 执行本地脚本
     */
    private String executeLocalScript(String script) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", script);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // 读取输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Local script execution timeout");
        }
        
        return output.toString().trim();
    }
    
    /**
     * 执行远程脚本（主入口方法）
     */
    public String executeScript(ServerAsset server, String script) {
        try {
            String ip = server.getIp();
            
            // 本机执行
            if ("127.0.0.1".equals(ip) || "localhost".equals(ip)) {
                log.debug("Executing script locally");
                return executeLocalScript(script);
            }
            
            // 远程执行（使用JSch）
            Session session = null;
            try {
                session = createSession(server);
                return executeRemoteCommand(session, script);
            } finally {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                    log.debug("SSH session disconnected");
                }
            }
            
        } catch (JSchException e) {
            String errorMsg = "SSH连接失败: " + e.getMessage();
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "命令执行失败: " + e.getMessage();
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "命令执行被中断";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = "SSH执行失败: " + e.getMessage();
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * 批量执行脚本
     */
    public Map<Long, String> batchExecute(List<ServerAsset> servers, String script) {
        Map<Long, String> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = servers.stream()
            .map(server -> CompletableFuture.runAsync(() -> {
                try {
                    String result = executeScript(server, script);
                    results.put(server.getId(), result);
                } catch (Exception e) {
                    results.put(server.getId(), "ERROR: " + e.getMessage());
                }
            }, executor))
            .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return results;
    }
    
    /**
     * 测试SSH连接
     */
    public boolean testConnection(ServerAsset server) {
        try {
            String result = executeScript(server, "echo 'OK'");
            boolean success = "OK".equals(result.trim());
            log.info("Connection test for {}@{}: {}", server.getUsername(), server.getIp(), 
                    success ? "SUCCESS" : "FAILED");
            return success;
        } catch (Exception e) {
            log.warn("Connection test failed for {}@{}: {}", 
                    server.getUsername(), server.getIp(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取系统信息（用于快速验证）
     */
    public Map<String, String> getSystemInfo(ServerAsset server) {
        Map<String, String> info = new HashMap<>();
        try {
            info.put("hostname", executeScript(server, "hostname"));
            info.put("os", executeScript(server, "uname -s"));
            info.put("kernel", executeScript(server, "uname -r"));
            info.put("uptime", executeScript(server, "uptime | awk -F'up ' '{print $2}' | awk -F',' '{print $1}'"));
        } catch (Exception e) {
            info.put("error", e.getMessage());
            log.error("Failed to get system info from {}@{}: {}", 
                    server.getUsername(), server.getIp(), e.getMessage());
        }
        return info;
    }
}
