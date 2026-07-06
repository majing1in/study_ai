package com.study.ai.spring.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 * <p>
 * 配置 MilvusVectorStore Bean，连接 Milvus 服务并管理 Collection 生命周期。
 * </p>
 * <p>
 * 架构说明：
 * <ul>
 *   <li>MilvusServiceClient — 底层 Milvus gRPC 连接客户端</li>
 *   <li>MilvusVectorStore — Spring AI 向量存储抽象，内置 EmbeddingModel 调用</li>
 * </ul>
 * </p>
 */
@Configuration
public class MilvusConfig {

    private static final Logger log = LoggerFactory.getLogger(MilvusConfig.class);

    @Value("${spring.ai.vectorstore.milvus.host}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.port}")
    private int port;

    @Value("${spring.ai.vectorstore.milvus.database-name}")
    private String databaseName;

    @Value("${spring.ai.vectorstore.milvus.collection-name}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.dimension}")
    private int dimension;

    @Value("${spring.ai.vectorstore.milvus.index-type}")
    private String indexType;

    @Value("${spring.ai.vectorstore.milvus.metric-type}")
    private String metricType;

    /**
     * 创建 MilvusServiceClient Bean
     * <p>
     * 这是与 Milvus 服务端的底层 gRPC 连接，由 MilvusVectorStore 内部使用。
     * </p>
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withDatabaseName(databaseName)
                .build();

        MilvusServiceClient client = new MilvusServiceClient(connectParam);
        log.info("MilvusServiceClient 已连接: {}:{}, database={}", host, port, databaseName);
        return client;
    }

    /**
     * 创建 MilvusVectorStore Bean
     * <p>
     * Spring AI 自动使用 EmbeddingModel 将 Document 文本转为向量后存入 Milvus，
     * 检索时也会自动将查询文本向量化后进行相似度搜索。
     * </p>
     * <p>
     * initializeSchema=true 表示应用启动时自动创建 Collection 和索引。
     * 生产环境首次启动后建议设为 false 以保留已有数据。
     * </p>
     */
    @Bean
    public MilvusVectorStore milvusVectorStore(MilvusServiceClient milvusServiceClient,
                                                EmbeddingModel embeddingModel) {
        MilvusVectorStore vectorStore = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .embeddingDimension(dimension)
                .indexType(IndexType.valueOf(indexType))
                .metricType(MetricType.valueOf(metricType))
                .initializeSchema(true)
                .build();

        log.info("MilvusVectorStore 初始化完成: collection={}, dimension={}, indexType={}, metricType={}",
                collectionName, dimension, indexType, metricType);
        return vectorStore;
    }
}
