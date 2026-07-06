package com.study.ai.spring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量存储服务
 * <p>
 * 封装 MilvusVectorStore 的常用操作，提供更高层的 API：
 * <ol>
 *   <li>addDocuments — 将文档向量化并存入 Milvus</li>
 *   <li>similaritySearch — 基于语义相似度检索文档</li>
 *   <li>deleteDocuments — 按 ID 删除文档</li>
 * </ol>
 * </p>
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final MilvusVectorStore vectorStore;

    public VectorStoreService(MilvusVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 将文档列表写入向量数据库
     * <p>
     * Spring AI 会自动调用 EmbeddingModel 将文档文本转为向量，
     * 然后将文本 + 向量 + 元数据一起存入 Milvus。
     * </p>
     *
     * @param documents 待写入的文档列表
     */
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("文档列表为空，跳过写入");
            return;
        }
        log.info("开始写入 {} 个文档到 Milvus", documents.size());
        vectorStore.add(documents);
        log.info("文档写入完成");
    }

    /**
     * 语义相似度检索
     * <p>
     * 将查询文本向量化后，在 Milvus 中搜索最相似的 TopK 个文档。
     * </p>
     *
     * @param query 查询文本
     * @param topK  返回的最相似文档数量
     * @return 相似文档列表（含相似度分数）
     */
    public List<Document> similaritySearch(String query, int topK) {
        log.info("执行相似度检索: query='{}', topK={}", query, topK);
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
        log.info("检索完成，返回 {} 个结果", results.size());
        return results;
    }

    /**
     * 带相似度阈值的语义检索
     * <p>
     * 只返回相似度 >= similarityThreshold 的结果。
     * 阈值范围 [0.0, 1.0]，0.0 表示接受所有结果，1.0 表示精确匹配。
     * </p>
     *
     * @param query               查询文本
     * @param topK                最大返回数量
     * @param similarityThreshold 相似度阈值（0.0 ~ 1.0）
     * @return 满足阈值条件的文档列表
     */
    public List<Document> similaritySearch(String query, int topK, double similarityThreshold) {
        log.info("执行相似度检索（阈值={}）: query='{}', topK={}", similarityThreshold, query, topK);
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build()
        );
        log.info("检索完成，返回 {} 个结果", results.size());
        return results;
    }

    /**
     * 带元数据过滤的语义检索
     * <p>
     * 支持 SQL 风格的过滤表达式，例如：
     * <pre>{@code
     * "country == 'UK' && year >= 2020 && isActive == true"
     * "city NOT IN ['Sofia', 'Plovdiv'] || price < 134.34"
     * }</pre>
     * </p>
     *
     * @param query            查询文本
     * @param topK             最大返回数量
     * @param filterExpression 元数据过滤表达式（SQL 风格），传 null 表示不过滤
     * @return 过滤后的文档列表
     */
    public List<Document> similaritySearch(String query, int topK, String filterExpression) {
        log.info("执行过滤检索: query='{}', topK={}, filter='{}'", query, topK, filterExpression);
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(filterExpression)
                        .build()
        );
        log.info("检索完成，返回 {} 个结果", results.size());
        return results;
    }

    /**
     * 按文档 ID 批量删除
     *
     * @param documentIds 待删除的文档 ID 列表
     */
    public void deleteDocuments(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        log.info("开始删除 {} 个文档", documentIds.size());
        vectorStore.delete(documentIds);
        log.info("文档删除完成");
    }
}
