package com.study.ai.spring.splitter;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义分片器：支持 chunkSize、overlap，并按段落拆分
 * <p>
 * 相比 Spring AI 原生的 TokenTextSplitter，增加了重叠（overlap）机制，
 * 在相邻文本块之间保留一定数量的字符，有效避免语义被切断，提高检索和召回的准确性。
 * </p>
 */
public class OverlapParagraphTextSplitter extends TextSplitter {

    // 每块最大字符数
    private final int chunkSize;
    // 相邻块之间重叠字符数
    private final int overlap;

    /**
     * @param chunkSize 每块最大字符数，必须大于 0
     * @param overlap   相邻块之间重叠字符数，必须 >= 0 且 < chunkSize
     */
    public OverlapParagraphTextSplitter(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须大于 0");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap 不能为负数");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap 不能大于等于 chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * 核心拆分逻辑：按段落（\\n+）拆分文本，再根据 chunkSize 和 overlap 进行分块
     */
    @Override
    protected List<String> splitText(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }

        String[] paragraphs = text.split("\\n+");
        List<String> allChunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (!StringUtils.hasText(paragraph)) {
                continue;
            }

            int start = 0;
            while (start < paragraph.length()) {
                int remainingSpace = chunkSize - currentChunk.length();
                int end = Math.min(start + remainingSpace, paragraph.length());

                if (currentChunk.length() > 0) {
                    currentChunk.append("\n");
                }
                currentChunk.append(paragraph, start, end);

                // 如果当前块已满，保存并生成新块
                if (currentChunk.length() >= chunkSize) {
                    allChunks.add(currentChunk.toString());

                    // 计算重叠：从前一个块的末尾截取 overlap 个字符作为新块的开头
                    String overlapText = "";
                    if (overlap > 0) {
                        int overlapStart = Math.max(0, currentChunk.length() - overlap);
                        overlapText = currentChunk.substring(overlapStart);
                    }

                    currentChunk = new StringBuilder();
                    if (!overlapText.isEmpty()) {
                        currentChunk.append(overlapText);
                    }
                }

                start = end;
            }
        }

        // 处理最后一个不足 chunkSize 的块
        if (!currentChunk.isEmpty()) {
            allChunks.add(currentChunk.toString());
        }

        return allChunks;
    }

    /**
     * 批量拆分：将多个 Document 分别拆分后合并返回
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            List<String> chunks = splitText(doc.getText());
            for (String chunk : chunks) {
                // 保留原始文档的元数据
                Document newDoc = new Document(chunk);
                newDoc.getMetadata().putAll(doc.getMetadata());
                result.add(newDoc);
            }
        }
        return result;
    }
}
