package com.sa.assistant.service;

import com.sa.assistant.common.exception.NonRetryableDocumentException;
import com.sa.assistant.common.exception.RetryableDocumentException;
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

    private static final int MAX_CONTENT_BYTES = 10 * 1024 * 1024;
    private static final long PARSE_TIMEOUT_SECONDS = 300;

    private final Tika tika = new Tika();

    /**
     * 解析文档文本内容。
     *
     * @throws NonRetryableDocumentException 文件不存在、内容过大
     * @throws RetryableDocumentException    解析超时、内部错误
     */
    public String parseDocument(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new NonRetryableDocumentException("文件不存在: " + filePath);
        }

        log.info("Parsing document | path={}", filePath);

        try (InputStream inputStream = Files.newInputStream(path)) {
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
            throw new RetryableDocumentException(String.format(
                    "文档解析超时（>%ds），文件可能过大或格式异常", PARSE_TIMEOUT_SECONDS), e);

        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (isContentTooLarge(cause)) {
                throw new NonRetryableDocumentException(String.format(
                        "文档内容过大（>%d MB），请拆分后重新上传", MAX_CONTENT_BYTES / (1024 * 1024)));
            }
            throw new RetryableDocumentException("文档解析失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause != null ? cause : e);

        } catch (Exception e) {
            log.error("Failed to parse document | path={}", filePath, e);
            throw new RetryableDocumentException("文档解析失败: " + e.getMessage(), e);
        }
    }

    public String detectMediaType(String filePath) {
        try {
            return tika.detect(Path.of(filePath));
        } catch (Exception e) {
            log.warn("Failed to detect media type | path={}", filePath, e);
            return "application/octet-stream";
        }
    }

    private String doParse(InputStream inputStream) throws Exception {
        Parser parser = new AutoDetectParser();
        StringWriter writer = new StringWriter();
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

    private boolean isContentTooLarge(Throwable cause) {
        return cause instanceof TikaException
                && cause.getCause() != null
                && cause.getCause().getMessage() != null
                && cause.getCause().getMessage().contains("maximum characters");
    }
}
