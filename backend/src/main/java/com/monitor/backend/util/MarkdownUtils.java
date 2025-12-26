package com.monitor.backend.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Markdown 工具类
 * <p>
 * 提供 Markdown 转 HTML 功能，用于告警邮件渲染
 * </p>
 */
public class MarkdownUtils {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        // 可以添加扩展配置，如表格、代码高亮等
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    /**
     * 将 Markdown 转换为 HTML
     *
     * @param markdown Markdown 文本
     * @return HTML 文本
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        Node document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }

    /**
     * 将 Markdown 转换为完整 HTML 邮件格式
     *
     * @param markdown Markdown 文本
     * @return 包含样式的完整 HTML
     */
    public static String toEmailHtml(String markdown) {
        String htmlContent = toHtml(markdown);
        return wrapWithEmailStyle(htmlContent);
    }

    /**
     * 为 HTML 内容添加邮件样式
     */
    private static String wrapWithEmailStyle(String htmlContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<style>");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        sb.append("line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }");
        sb.append("h1, h2, h3 { color: #2c3e50; margin-top: 20px; }");
        sb.append("code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: monospace; }");
        sb.append("pre { background: #2d2d2d; color: #f8f8f2; padding: 12px; border-radius: 6px; overflow-x: auto; }");
        sb.append("pre code { background: transparent; padding: 0; }");
        sb.append("table { border-collapse: collapse; width: 100%; margin: 16px 0; }");
        sb.append("th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }");
        sb.append("th { background: #f5f5f5; font-weight: 600; }");
        sb.append("blockquote { border-left: 4px solid #ddd; margin: 16px 0; padding: 0 16px; color: #666; }");
        sb.append("a { color: #3498db; text-decoration: none; }");
        sb.append(".alert { padding: 12px 16px; border-radius: 6px; margin: 16px 0; }");
        sb.append(".alert-warning { background: #fff3cd; border: 1px solid #ffc107; color: #856404; }");
        sb.append(".alert-danger { background: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; }");
        sb.append("</style></head><body>");
        sb.append(htmlContent);
        sb.append("</body></html>");
        return sb.toString();
    }
}
