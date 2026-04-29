package com.sa.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RAG 流式响应事件。
 *
 * <p>type 字段区分事件类型：
 * <ul>
 *   <li>{@code chunk} — LLM 生成的文本片段</li>
 *   <li>{@code source} — 检索到的文档来源信息</li>
 *   <li>{@code done} — 流式输出结束</li>
 *   <li>{@code error} — 发生错误</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamEvent(
        String type,
        String content,
        String source,
        Integer relevantChunkCount,
        String contextPreview,
        String message
) {
    public static StreamEvent chunk(String content) {
        return new StreamEvent("chunk", content, null, null, null, null);
    }

    public static StreamEvent source(String source, int relevantChunkCount, String contextPreview) {
        return new StreamEvent("source", null, source, relevantChunkCount, contextPreview, null);
    }

    public static StreamEvent done() {
        return new StreamEvent("done", null, null, null, null, null);
    }

    public static StreamEvent error(String message) {
        return new StreamEvent("error", null, null, null, null, message);
    }
}
