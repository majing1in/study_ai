package com.study.ai.spring.splitter;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 递归字符文本分片器
 * <p>
 * 参考 Spring AI Alibaba 的 RecursiveCharacterTextSplitter 实现。
 * 按照分隔符优先级列表递归拆分文本，优先使用高优先级分隔符（如段落、句子），
 * 确保每个分块大小不超过 chunkSize，同时尽量保持语义完整性。
 * </p>
 * <p>
 * 分隔符优先级（从高到低）：
 * <ol>
 *   <li>"\\n\\n" — 段落分隔（双换行）</li>
 *   <li>"\\n" — 行分隔（单换行）</li>
 *   <li>"。" — 中文句号</li>
 *   <li>"！" — 中文感叹号</li>
 *   <li>"？" — 中文问号</li>
 *   <li>"." — 英文句号</li>
 *   <li>"!" — 英文感叹号</li>
 *   <li>"?" — 英文问号</li>
 *   <li>"；" / ";" — 分号</li>
 *   <li>"，" / "," — 逗号</li>
 *   <li>" " — 空格</li>
 *   <li>"" — 字符级拆分（最终兜底）</li>
 * </ol>
 * </p>
 */
public class RecursiveCharacterTextSplitter {

    /**
     * 分隔符优先级列表：从大到小，优先保持较大的语义单元
     */
    private static final List<String> DEFAULT_SEPARATORS = Collections.unmodifiableList(Arrays.asList(
            "\n\n",     // 段落分隔
            "\n",       // 行分隔
            "。",       // 中文句号
            "！",       // 中文感叹号
            "？",       // 中文问号
            ". ",       // 英文句号+空格（避免拆开缩写）
            "! ",       // 英文感叹号
            "? ",       // 英文问号
            "；",       // 中文分号
            ";",        // 英文分号
            "，",       // 中文逗号
            ",",        // 英文逗号
            " ",        // 空格
            ""          // 字符级兜底
    ));

    // 每个分块的目标最大字符数
    private final int chunkSize;

    // 相邻块之间的重叠字符数
    private final int overlap;

    // 自定义分隔符列表（可选）
    private final List<String> separators;

    /**
     * 使用默认分隔符创建分片器
     *
     * @param chunkSize 每个分块的目标最大字符数
     */
    public RecursiveCharacterTextSplitter(int chunkSize) {
        this(chunkSize, 0, DEFAULT_SEPARATORS);
    }

    /**
     * 使用默认分隔符创建分片器（带重叠）
     *
     * @param chunkSize 每个分块的目标最大字符数
     * @param overlap   相邻块之间的重叠字符数
     */
    public RecursiveCharacterTextSplitter(int chunkSize, int overlap) {
        this(chunkSize, overlap, DEFAULT_SEPARATORS);
    }

    /**
     * 使用自定义分隔符创建分片器
     *
     * @param chunkSize  每个分块的目标最大字符数
     * @param overlap    相邻块之间的重叠字符数
     * @param separators 自定义分隔符优先级列表
     */
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

    /**
     * 拆分文本
     *
     * @param text 待拆分的文本
     * @return 拆分后的文本块列表
     */
    public List<String> splitText(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> chunks = splitRecursively(text, 0);
        return mergeWithOverlap(chunks);
    }

    /**
     * 递归拆分：在当前分隔符级别尝试拆分，若块仍大于 chunkSize 则递归降级
     */
    private List<String> splitRecursively(String text, int separatorIndex) {
        // 如果当前块已经足够小，直接返回
        if (text.length() <= chunkSize) {
            return Collections.singletonList(text);
        }

        // 如果已经用完了所有分隔符，按字符强制截断
        if (separatorIndex >= separators.size()) {
            return splitByCharacter(text);
        }

        String separator = separators.get(separatorIndex);

        // 空字符串分隔符表示字符级拆分（最终兜底）
        if (separator.isEmpty()) {
            return splitByCharacter(text);
        }

        // 如果当前分隔符在文本中不存在，尝试下一级分隔符
        if (!text.contains(separator)) {
            return splitRecursively(text, separatorIndex + 1);
        }

        // 按当前分隔符拆分
        String[] parts = text.split(separator, -1);
        List<String> result = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String partWithSep = (i < parts.length - 1) ? part + separator : part;

            // 如果加上当前部分后超出 chunkSize
            if (currentChunk.length() + partWithSep.length() > chunkSize && currentChunk.length() > 0) {
                // 对当前已累积的块进行递归处理（使用下一级分隔符）
                String accumulated = currentChunk.toString();
                if (accumulated.length() > chunkSize) {
                    result.addAll(splitRecursively(accumulated, separatorIndex + 1));
                } else {
                    result.add(accumulated);
                }
                currentChunk = new StringBuilder();
            }

            // 如果单个部分本身就超过 chunkSize，需要递归降级处理
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

        // 处理最后一个块
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

    /**
     * 按字符硬截断（最终兜底策略）
     */
    private List<String> splitByCharacter(String text) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
        }
        return chunks;
    }

    /**
     * 为相邻块之间添加重叠
     */
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
