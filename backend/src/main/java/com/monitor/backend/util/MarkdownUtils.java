package com.monitor.backend.util;

/**
 * Markdown 工具类
 * 提供 Markdown 格式处理的相关方法
 */
public class MarkdownUtils {

    private MarkdownUtils() {
        // 工具类禁止实例化
    }

    /**
     * 去除 Markdown 格式符号，将段落用逗号分隔
     * 适用于短信发送等纯文本场景
     *
     * @param content Markdown 格式的内容
     * @return 纯文本内容，段落之间用中文逗号分隔
     */
    public static String stripMarkdown(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String result = content;

        // 移除代码块
        result = result.replaceAll("```[\\s\\S]*?```", "");
        result = result.replaceAll("`([^`]+)`", "$1");

        // 移除标题符号 (# ## ### 等)
        result = result.replaceAll("(?m)^#{1,6}\\s*", "");

        // 移除加粗和斜体 (**text**, *text*, __text__, _text_)
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        result = result.replaceAll("\\*(.+?)\\*", "$1");
        result = result.replaceAll("__(.+?)__", "$1");
        result = result.replaceAll("_(.+?)_", "$1");

        // 移除删除线 ~~text~~
        result = result.replaceAll("~~(.+?)~~", "$1");

        // 移除链接 [text](url) -> text
        result = result.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");

        // 移除图片 ![alt](url)
        result = result.replaceAll("!\\[([^\\]]*)\\]\\([^)]+\\)", "");

        // 移除引用符号 >
        result = result.replaceAll("(?m)^>\\s*", "");

        // 移除列表符号 (-, *, +, 1. 2. 等)
        result = result.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        result = result.replaceAll("(?m)^\\s*\\d+\\.\\s+", "");

        // 移除水平分隔线 (---, ***, ___)
        result = result.replaceAll("(?m)^[-*_]{3,}\\s*$", "");

        // 将多个换行替换为逗号
        result = result.replaceAll("\\n{2,}", "，");

        // 单个换行也替换为逗号
        result = result.replaceAll("\\n", "，");

        // 清理多余的逗号和空格
        result = result.replaceAll("，{2,}", "，");
        result = result.replaceAll("^，|，$", "");
        result = result.trim();

        return result;
    }

    /**
     * 将 Markdown 转换为 HTML（简易实现）
     * 支持基本的 Markdown 语法
     *
     * @param markdown Markdown 格式的内容
     * @return HTML 内容
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }

        String result = markdown;

        // 代码块
        result = result.replaceAll("```([\\s\\S]*?)```", "<pre><code>$1</code></pre>");
        result = result.replaceAll("`([^`]+)`", "<code>$1</code>");

        // 标题
        result = result.replaceAll("(?m)^######\\s*(.+)$", "<h6>$1</h6>");
        result = result.replaceAll("(?m)^#####\\s*(.+)$", "<h5>$1</h5>");
        result = result.replaceAll("(?m)^####\\s*(.+)$", "<h4>$1</h4>");
        result = result.replaceAll("(?m)^###\\s*(.+)$", "<h3>$1</h3>");
        result = result.replaceAll("(?m)^##\\s*(.+)$", "<h2>$1</h2>");
        result = result.replaceAll("(?m)^#\\s*(.+)$", "<h1>$1</h1>");

        // 加粗和斜体
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        result = result.replaceAll("\\*(.+?)\\*", "<em>$1</em>");

        // 链接
        result = result.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");

        // 换行转 <br>
        result = result.replaceAll("\\n", "<br>");

        return result;
    }
}
