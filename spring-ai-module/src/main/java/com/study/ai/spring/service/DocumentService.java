package com.study.ai.spring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.study.ai.spring.splitter.OverlapParagraphTextSplitter;
import com.study.ai.spring.splitter.RecursiveCharacterTextSplitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档处理服务
 * <p>
 * 负责文档的加载、清洗和分片，涵盖完整的 ETL Pipeline：
 * <ol>
 *   <li>Extract — 使用 TikaDocumentReader 加载文档</li>
 *   <li>Transform — 文本清洗 + 文档分片</li>
 *   <li>Load — 返回处理后的 Document 列表</li>
 * </ol>
 * </p>
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    // 匹配多余空白字符（多个空格、制表符等）
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+");
    // 匹配超过3个连续换行符
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{4,}");

    /**
     * 读取并处理文档（使用 TokenTextSplitter 分片）
     *
     * @param filePath 文件路径
     * @return 处理后的 Document 列表
     */
    public List<Document> readAndSplitWithToken(String filePath) {
        return readAndSplitWithToken(filePath, 600, 300, 5, 8000, true);
    }

    /**
     * 读取并处理文档（使用 TokenTextSplitter 分片，可自定义参数）
     *
     * @param filePath            文件路径
     * @param chunkSize           每块目标 token 数
     * @param minChunkSizeChars   每块最小字符数
     * @param minChunkLengthToEmbed 最小嵌入长度
     * @param maxNumChunks        最大分块数
     * @param keepSeparator       是否保留分隔符
     * @return 处理后的 Document 列表
     */
    public List<Document> readAndSplitWithToken(String filePath,
                                                 int chunkSize,
                                                 int minChunkSizeChars,
                                                 int minChunkLengthToEmbed,
                                                 int maxNumChunks,
                                                 boolean keepSeparator) {
        try {
            // 1. 加载文档
            List<Document> documents = loadDocuments(filePath);
            log.info("加载文档完成，共 {} 个文档", documents.size());

            // 2. 文本清洗
            documents = cleanDocuments(documents);
            log.info("文本清洗完成");

            // 3. 文档分片（TokenTextSplitter）
            documents = splitWithToken(documents, chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator);
            log.info("TokenTextSplitter 分片完成，共 {} 个分块", documents.size());

            return documents;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取并处理文档（使用 OverlapParagraphTextSplitter 分片）
     *
     * @param filePath  文件路径
     * @param chunkSize 每块最大字符数
     * @param overlap   相邻块重叠字符数
     * @return 处理后的 Document 列表
     */
    public List<Document> readAndSplitWithOverlap(String filePath, int chunkSize, int overlap) {
        try {
            // 1. 加载文档
            List<Document> documents = loadDocuments(filePath);
            log.info("加载文档完成，共 {} 个文档", documents.size());

            // 2. 文本清洗
            documents = cleanDocuments(documents);
            log.info("文本清洗完成");

            // 3. 文档分片（OverlapParagraphTextSplitter）
            OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(chunkSize, overlap);
            List<Document> result = splitter.apply(documents);
            log.info("OverlapParagraphTextSplitter 分片完成，共 {} 个分块", result.size());

            return result;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取并处理文档（使用 RecursiveCharacterTextSplitter 分片）
     *
     * @param filePath  文件路径
     * @param chunkSize 每块最大字符数
     * @param overlap   相邻块重叠字符数（传 0 表示不重叠）
     * @return 处理后的 Document 列表
     */
    public List<Document> readAndSplitWithRecursive(String filePath, int chunkSize, int overlap) {
        try {
            // 1. 加载文档
            List<Document> documents = loadDocuments(filePath);
            log.info("加载文档完成，共 {} 个文档", documents.size());

            // 注意：递归分片需要保留换行等特殊符号来识别语义边界，
            // 所以这里不调用 cleanDocuments，只做基本的文本清洗
            documents = basicCleanDocuments(documents);
            log.info("基础清洗完成");

            // 3. 文档分片（RecursiveCharacterTextSplitter）
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(chunkSize, overlap);
            List<Document> result = new ArrayList<>();
            for (Document doc : documents) {
                List<String> chunks = splitter.splitText(doc.getText());
                for (String chunk : chunks) {
                    Document newDoc = new Document(chunk);
                    newDoc.getMetadata().putAll(doc.getMetadata());
                    result.add(newDoc);
                }
            }
            log.info("RecursiveCharacterTextSplitter 分片完成，共 {} 个分块", result.size());

            return result;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载文档：使用 TikaDocumentReader 读取各种格式的文件
     * <p>
     * 支持格式：PDF、Word、HTML、TXT、Markdown 等
     * </p>
     */
    public List<Document> loadDocuments(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件: " + filePath);
        }

        Resource resource = new FileSystemResource(filePath);
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        return reader.read();
    }

    /**
     * 文本清洗：移除多余空白、统一换行格式等
     * <p>
     * 适用于 TokenTextSplitter 和 OverlapParagraphTextSplitter
     * </p>
     */
    public List<Document> cleanDocuments(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        List<Document> cleaned = new ArrayList<>();
        for (Document doc : documents) {
            String text = doc.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }

            // 1. 统一换行符
            text = text.replace("\r\n", "\n").replace("\r", "\n");

            // 2. 压缩多余空白行（超过3个连续换行符合并为2个）
            text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");

            // 3. 压缩多余空格和制表符
            text = MULTI_SPACE.matcher(text).replaceAll(" ");

            // 4. 去除首尾空白
            text = text.trim();

            if (StringUtils.hasText(text)) {
                Document cleanedDoc = new Document(text);
                cleanedDoc.getMetadata().putAll(doc.getMetadata());
                cleaned.add(cleanedDoc);
            }
        }

        return cleaned;
    }

    /**
     * 基础文本清洗：仅做最基本的处理，保留换行等语义符号
     * <p>
     * 适用于 RecursiveCharacterTextSplitter，因为递归分片依赖换行、句号等符号来识别语义边界
     * </p>
     */
    public List<Document> basicCleanDocuments(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        List<Document> cleaned = new ArrayList<>();
        for (Document doc : documents) {
            String text = doc.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }

            // 1. 统一换行符
            text = text.replace("\r\n", "\n").replace("\r", "\n");

            // 2. 压缩多余空白行（超过3个连续换行符合并为2个）
            text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");

            // 3. 去除首尾空白
            text = text.trim();

            if (StringUtils.hasText(text)) {
                Document cleanedDoc = new Document(text);
                cleanedDoc.getMetadata().putAll(doc.getMetadata());
                cleaned.add(cleanedDoc);
            }
        }

        return cleaned;
    }

    /**
     * 使用 TokenTextSplitter 分片
     */
    private List<Document> splitWithToken(List<Document> documents,
                                           int chunkSize,
                                           int minChunkSizeChars,
                                           int minChunkLengthToEmbed,
                                           int maxNumChunks,
                                           boolean keepSeparator) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize,
                minChunkSizeChars,
                minChunkLengthToEmbed,
                maxNumChunks,
                keepSeparator
        );
        return splitter.apply(documents);
    }
}
