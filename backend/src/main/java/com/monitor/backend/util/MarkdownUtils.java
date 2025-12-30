package com.monitor.backend.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Markdown 工具类
 * 提供 Markdown 格式处理的相关方法
 */
public class MarkdownUtils {

    // Flexmark 解析器和渲染器（线程安全，可复用）
    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        // 软换行转为 <br> 标签（单个换行符也会转换）
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        // 硬换行转为 <br> 标签
        options.set(HtmlRenderer.HARD_BREAK, "<br />\n");
        
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

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
     * 将 Markdown 转换为 HTML
     * 使用 Flexmark 库实现完整的 Markdown 解析
     * 支持标题、加粗、斜体、链接、代码块、列表等完整语法
     *
     * @param markdown Markdown 格式的内容
     * @return HTML 内容
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }

        // 预处理：在中文字符后的强调符号前添加零宽空格，确保正确解析
        // 这是因为 Flexmark 需要词边界来识别强调符号，中文没有词边界
        String content = preprocessMarkdown(markdown);
        
        // 确保内容末尾有换行符，避免解析器遗漏末尾的 Markdown 符号
        content = content.endsWith("\n") ? content : content + "\n";
        Node document = PARSER.parse(content);
        return RENDERER.render(document).trim();
    }

    /**
     * 预处理 Markdown 内容
     * 在中文字符与嵌套强调符号（3个及以上的 * 或 _）之间添加空格
     * 确保 Flexmark 能正确识别词边界，解决 ***text*** 在中文后无法正确解析的问题
     * 只处理嵌套强调符号，不影响普通的 ** 或 * 格式
     */
    private static String preprocessMarkdown(String markdown) {
        String result = markdown;
        
        // 只处理嵌套强调符号（3个及以上的 * 或 _）
        // 匹配: 中文字符后紧跟 ***、___、**** 等
        result = result.replaceAll("([\\u4e00-\\u9fa5])([*]{3,})", "$1 $2");
        result = result.replaceAll("([\\u4e00-\\u9fa5])([_]{3,})", "$1 $2");
        // 匹配: ***、___、**** 等后紧跟中文字符
        result = result.replaceAll("([*]{3,})([\\u4e00-\\u9fa5])", "$1 $2");
        result = result.replaceAll("([_]{3,})([\\u4e00-\\u9fa5])", "$1 $2");
        
        return result;
    }
}
