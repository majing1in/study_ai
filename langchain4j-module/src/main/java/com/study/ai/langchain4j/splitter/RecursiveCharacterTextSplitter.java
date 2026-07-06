package com.study.ai.langchain4j.splitter;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 递归字符文本分片器（LangChain4j 模块独立版本）
 * <p>
 * 按照分隔符优先级列表递归拆分文本，优先使用高优先级分隔符（如段落、句子），
 * 确保每个分块大小不超过 chunkSize，同时尽量保持语义完整性。
 * </p>
 */
public class RecursiveCharacterTextSplitter {

    /**
     * 默认分隔符优先级列表
     */
    private static final List<String> DEFAULT_SEPARATORS = Collections.unmodifiableList(Arrays.asList(
            "\n\n",     // 段落分隔
            "\n",       // 行分隔
            "。",       // 中文句号
            "！",       // 中文感叹号
            "？",       // 中文问号
            ". ",       // 英文句号+空格
            "! ",       // 英文感叹号
            "? ",       // 英文问号
            "；",       // 中文分号
            ";",        // 英文分号
            "，",       // 中文逗号
            ",",        // 英文逗号
            " ",        // 空格
            ""          // 字符级兜底
    ));

    private final int chunkSize;
    private final int overlap;
    private final List<String> separators;

    public RecursiveCharacterTextSplitter(int chunkSize) {
        this(chunkSize, 0, DEFAULT_SEPARATORS);
    }

    public RecursiveCharacterTextSplitter(int chunkSize, int overlap) {
        this(chunkSize, overlap, DEFAULT_SEPARATORS);
    }

    public RecursiveCharacterTextSplitter(int chunkSize, int overlap, List<String> separators) {
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
        this.separators = separators != null ? new ArrayList<>(separators) : new ArrayList<>(DEFAULT_SEPARATORS);
    }

    public List<String> splitText(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> chunks = splitRecursively(text, 0);
        return mergeWithOverlap(chunks);
    }

    private List<String> splitRecursively(String text, int separatorIndex) {
        if (text.length() <= chunkSize) {
            return Collections.singletonList(text);
        }

        if (separatorIndex >= separators.size()) {
            return splitByCharacter(text);
        }

        String separator = separators.get(separatorIndex);

        if (separator.isEmpty()) {
            return splitByCharacter(text);
        }

        if (!text.contains(separator)) {
            return splitRecursively(text, separatorIndex + 1);
        }

        String[] parts = text.split(separator, -1);
        List<String> result = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String partWithSep = (i < parts.length - 1) ? part + separator : part;

            if (currentChunk.length() + partWithSep.length() > chunkSize && currentChunk.length() > 0) {
                String accumulated = currentChunk.toString();
                if (accumulated.length() > chunkSize) {
                    result.addAll(splitRecursively(accumulated, separatorIndex + 1));
                } else {
                    result.add(accumulated);
                }
                currentChunk = new StringBuilder();
            }

            if (partWithSep.length() > chunkSize) {
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                result.addAll(splitRecursively(partWithSep, separatorIndex + 1));
            } else {
                currentChunk.append(partWithSep);
            }
        }

        if (currentChunk.length() > 0) {
            String lastChunk = currentChunk.toString();
            if (lastChunk.length() > chunkSize) {
                result.addAll(splitRecursively(lastChunk, separatorIndex + 1));
            } else {
                result.add(lastChunk);
            }
        }

        return result;
    }

    private List<String> splitByCharacter(String text) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
        }
        return chunks;
    }

    private List<String> mergeWithOverlap(List<String> chunks) {
        if (overlap <= 0 || chunks.size() <= 1) {
            return chunks;
        }

        List<String> result = new ArrayList<>();
        result.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String prevChunk = chunks.get(i - 1);
            String currChunk = chunks.get(i);

            if (prevChunk.length() > overlap) {
                String overlapText = prevChunk.substring(prevChunk.length() - overlap);
                currChunk = overlapText + currChunk;
            }

            result.add(currChunk);
        }

        return result;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }
}
