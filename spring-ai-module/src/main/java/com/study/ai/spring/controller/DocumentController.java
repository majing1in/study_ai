package com.study.ai.spring.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.study.ai.spring.service.DocumentService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档处理 REST 接口
 * <p>
 * 提供文档加载、清洗、分片的完整 ETL Pipeline 接口，
 * 支持三种分片方式：
 * <ol>
 *   <li>TokenTextSplitter — Spring AI 原生固定长度分片</li>
 *   <li>OverlapParagraphTextSplitter — 自定义重叠段落分片</li>
 *   <li>RecursiveCharacterTextSplitter — 递归字符分片</li>
 * </ol>
 * </p>
 */
@RestController
@RequestMapping("/document")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 读取文档（使用 TokenTextSplitter 分片）
     * <p>
     * 对应文章中的基础分片方式，按 Token 数量等长切分。
     * </p>
     *
     * @param path 文件路径
     * @return 分片结果（含分块列表和统计信息）
     */
    @GetMapping("/read")
    public Map<String, Object> readDocument(@RequestParam("path") String path) {
        log.info("收到文档处理请求（TokenTextSplitter）: {}", path);

        List<Document> documents = documentService.readAndSplitWithToken(path);

        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", documents.size());
        result.put("splitter", "TokenTextSplitter");
        result.put("chunks", documents);
        return result;
    }

    /**
     * 读取文档（使用 TokenTextSplitter，可自定义参数）
     *
     * @param path                 文件路径
     * @param chunkSize            每块目标 token 数（默认 600）
     * @param minChunkSizeChars    每块最小字符数（默认 300）
     * @param minChunkLengthToEmbed 最小嵌入长度（默认 5）
     * @param maxNumChunks         最大分块数（默认 8000）
     * @param keepSeparator        是否保留分隔符（默认 true）
     * @return 分片结果
     */
    @GetMapping("/read/token")
    public Map<String, Object> readWithToken(
            @RequestParam("path") String path,
            @RequestParam(value = "chunkSize", defaultValue = "600") int chunkSize,
            @RequestParam(value = "minChunkSizeChars", defaultValue = "300") int minChunkSizeChars,
            @RequestParam(value = "minChunkLengthToEmbed", defaultValue = "5") int minChunkLengthToEmbed,
            @RequestParam(value = "maxNumChunks", defaultValue = "8000") int maxNumChunks,
            @RequestParam(value = "keepSeparator", defaultValue = "true") boolean keepSeparator) {

        log.info("收到文档处理请求（TokenTextSplitter 自定义参数）: {}", path);

        List<Document> documents = documentService.readAndSplitWithToken(
                path, chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator);

        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", documents.size());
        result.put("splitter", "TokenTextSplitter");
        result.put("params", Map.of(
                "chunkSize", chunkSize,
                "minChunkSizeChars", minChunkSizeChars,
                "minChunkLengthToEmbed", minChunkLengthToEmbed,
                "maxNumChunks", maxNumChunks,
                "keepSeparator", keepSeparator
        ));
        result.put("chunks", documents);
        return result;
    }

    /**
     * 读取文档（使用 OverlapParagraphTextSplitter 分片）
     * <p>
     * 对应文章中的自定义重叠分片器，支持 chunkSize 和 overlap 参数，
     * 在相邻文本块之间保留重叠内容，避免语义被切断。
     * </p>
     *
     * @param path      文件路径
     * @param chunkSize 每块最大字符数（默认 400）
     * @param overlap   相邻块重叠字符数（默认 100）
     * @return 分片结果
     */
    @GetMapping("/read/overlap")
    public Map<String, Object> readWithOverlap(
            @RequestParam("path") String path,
            @RequestParam(value = "chunkSize", defaultValue = "400") int chunkSize,
            @RequestParam(value = "overlap", defaultValue = "100") int overlap) {

        log.info("收到文档处理请求（OverlapParagraphTextSplitter）: {}, chunkSize={}, overlap={}",
                path, chunkSize, overlap);

        List<Document> documents = documentService.readAndSplitWithOverlap(path, chunkSize, overlap);

        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", documents.size());
        result.put("splitter", "OverlapParagraphTextSplitter");
        result.put("params", Map.of(
                "chunkSize", chunkSize,
                "overlap", overlap
        ));
        result.put("chunks", documents);
        return result;
    }

    /**
     * 读取文档（使用 RecursiveCharacterTextSplitter 分片）
     * <p>
     * 对应文章中的递归分片方式，按照分隔符优先级递归拆分文本，
     * 优先保持句子和段落的完整性。
     * 注意：使用此接口需要保留文档中的换行等语义符号。
     * </p>
     *
     * @param path      文件路径
     * @param chunkSize 每块最大字符数（默认 100）
     * @param overlap   重叠字符数（默认 0，因为 Spring AI Alibaba 版本不支持 overlap）
     * @return 分片结果
     */
    @GetMapping("/read/recursive")
    public Map<String, Object> readWithRecursive(
            @RequestParam("path") String path,
            @RequestParam(value = "chunkSize", defaultValue = "100") int chunkSize,
            @RequestParam(value = "overlap", defaultValue = "0") int overlap) {

        log.info("收到文档处理请求（RecursiveCharacterTextSplitter）: {}, chunkSize={}, overlap={}",
                path, chunkSize, overlap);

        List<Document> documents = documentService.readAndSplitWithRecursive(path, chunkSize, overlap);

        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", documents.size());
        result.put("splitter", "RecursiveCharacterTextSplitter");
        result.put("params", Map.of(
                "chunkSize", chunkSize,
                "overlap", overlap
        ));
        result.put("chunks", documents);
        return result;
    }

    /**
     * 纯文本递归分片（直接输入文本，不需要文件路径）
     * <p>
     * 对应文章中直接对字符串进行递归分片的示例
     * </p>
     *
     * @param text      待分片文本
     * @param chunkSize 每块最大字符数（默认 100）
     * @return 分片结果
     */
    @GetMapping("/read/recursive-text")
    public Map<String, Object> readWithRecursiveText(
            @RequestParam("text") String text,
            @RequestParam(value = "chunkSize", defaultValue = "100") int chunkSize) {

        log.info("收到文本递归分片请求，文本长度: {}", text.length());

        com.study.ai.spring.splitter.RecursiveCharacterTextSplitter splitter =
                new com.study.ai.spring.splitter.RecursiveCharacterTextSplitter(chunkSize);

        List<String> chunks = splitter.splitText(text);

        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", chunks.size());
        result.put("splitter", "RecursiveCharacterTextSplitter");
        result.put("params", Map.of("chunkSize", chunkSize));
        result.put("chunks", chunks);
        return result;
    }
}
