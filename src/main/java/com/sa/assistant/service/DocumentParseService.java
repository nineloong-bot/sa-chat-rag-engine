package com.sa.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class DocumentParseService {

    /**
     * 单次解析最大内容长度：10MB（UTF-8 约 1000 万字符）。
     * 超过此限制时 Tika 会抛出 SAXException，防止大文件撑爆堆内存。
     */
    private static final int MAX_CONTENT_BYTES = 10 * 1024 * 1024;

    /**
     * 解析超时时间：5 分钟。
     * 超过此时间认为 Tika 引擎卡死，强制中断解析线程。
     */
    private static final long PARSE_TIMEOUT_SECONDS = 300;

    private final Tika tika = new Tika();

    /**
     * 解析文档文本内容。
     *
     * @throws RuntimeException 文件不存在、解析超时、内容过大、或 Tika 内部错误
     */
    public String parseDocument(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new RuntimeException("文件不存在: " + filePath);
        }

        log.info("Parsing document | path={}, size={}", filePath,
                filePath.length() > 50 ? "[large file]" : filePath);

        try (InputStream inputStream = Files.newInputStream(path)) {
            // 使用 CompletableFuture 实现解析超时控制
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return doParse(inputStream);
                } catch (Exception e) {
                    throw new RuntimeException("Tika parse error", e);
                }
            });

            String content = future.get(PARSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Document parsed | path={}, contentLength={}",
                    filePath, content != null ? content.length() : 0);
            return content;

        } catch (TimeoutException e) {
            log.error("Document parse timed out after {}s | path={}", PARSE_TIMEOUT_SECONDS, filePath);
            throw new RuntimeException(String.format(
                    "文档解析超时（>%ds），文件可能过大或格式异常，请尝试拆分后重新上传", PARSE_TIMEOUT_SECONDS), e);

        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("Document parse execution failed | path={}, cause={}", filePath,
                    cause != null ? cause.getMessage() : e.getMessage());
            // 检查是否是内容过大导致的
            if (cause instanceof TikaException
                    && cause.getCause() != null
                    && cause.getCause().getMessage() != null
                    && cause.getCause().getMessage().contains("maximum characters")) {
                throw new RuntimeException(String.format(
                        "文档内容过大（>%d MB），请拆分后重新上传", MAX_CONTENT_BYTES / (1024 * 1024)), cause);
            }
            throw new RuntimeException("文档解析失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);

        } catch (Exception e) {
            log.error("Failed to parse document | path={}", filePath, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 实际 Tika 解析逻辑，运行在独立线程中。
     * 通过 ContentHandler 限制输出大小，防止大文件 OOM。
     */
    private String doParse(InputStream inputStream) throws Exception {
        Parser parser = new AutoDetectParser();
        StringWriter writer = new StringWriter();

        // WriteOutContentHandler：超过 MAX_CONTENT_BYTES 时抛出 SAXException
        WriteOutContentHandler writeHandler = new WriteOutContentHandler(writer, MAX_CONTENT_BYTES);
        BodyContentHandler handler = new BodyContentHandler(writeHandler);

        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        parser.parse(inputStream, handler, metadata, context);

        String content = writer.toString();

        if (content != null) {
            content = content.replaceAll("\\s+", " ").trim();
        }

        return content;
    }

    /**
     * 检测文件 MIME 类型。
     */
    public String detectMediaType(String filePath) {
        try {
            return tika.detect(Path.of(filePath));
        } catch (Exception e) {
            log.warn("Failed to detect media type | path={}", filePath, e);
            return "application/octet-stream";
        }
    }
}
