package com.study.ai.langchain4j.controller;

import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j 文档分片演示接口
 * <p>
 * 演示 LangChain4j 的语义分片能力：
 * <ol>
 *   <li>DocumentBySentenceSplitter — 按句子语义分片</li>
 * </ol>
 * </p>
 * <p>
 * 注意：DocumentBySentenceSplitter 基于英文句号进行句子检测，
 * 对中文的支持有限。中文文本建议使用 Spring AI 的递归分片器。
 * </p>
 */
@RestController
@RequestMapping("/split")
public class SplitController {

    private static final Logger log = LoggerFactory.getLogger(SplitController.class);

    /**
     * 使用 DocumentBySentenceSplitter 进行语义分片
     * <p>
     * 对应文章中的 LangChain4j 语义分段示例。
     * 基于句子语义边界进行拆分，适合英文文本。
     * </p>
     *
     * @param text            待分片文本
     * @param maxSegmentSize  最大段大小（字符数，默认 100）
     * @param maxOverlapSize  最大重叠大小（字符数，默认 10）
     * @return 分片结果
     */
    @GetMapping("/sentence")
    public Map<String, Object> splitBySentence(
            @RequestParam("text") String text,
            @RequestParam(value = "maxSegmentSize", defaultValue = "100") int maxSegmentSize,
            @RequestParam(value = "maxOverlapSize", defaultValue = "10") int maxOverlapSize) {

        log.info("收到语义分片请求（DocumentBySentenceSplitter），文本长度: {}", text.length());

        DocumentBySentenceSplitter splitter = new DocumentBySentenceSplitter(maxSegmentSize, maxOverlapSize);
        String[] segments = splitter.split(text);

        List<String> chunks = Arrays.asList(segments);

        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", chunks.size());
        result.put("splitter", "DocumentBySentenceSplitter (LangChain4j)");
        result.put("params", Map.of(
                "maxSegmentSize", maxSegmentSize,
                "maxOverlapSize", maxOverlapSize
        ));
        result.put("chunks", chunks);
        return result;
    }

    /**
     * 中文文本递归分片（使用自定义 RecursiveCharacterTextSplitter）
     * <p>
     * 由于 LangChain4j 的 DocumentBySentenceSplitter 对中文支持不足，
     * 这里提供一个中文友好的递归分片接口。
     * </p>
     *
     * @param text      待分片文本
     * @param chunkSize 每块最大字符数（默认 100）
     * @return 分片结果
     */
    @GetMapping("/recursive-chinese")
    public Map<String, Object> splitChineseText(
            @RequestParam("text") String text,
            @RequestParam(value = "chunkSize", defaultValue = "100") int chunkSize) {

        log.info("收到中文递归分片请求，文本长度: {}", text.length());

        // 针对中文优化的分隔符列表
        List<String> chineseSeparators = Arrays.asList(
                "\n\n",     // 段落分隔
                "\n",       // 行分隔
                "。",       // 中文句号
                "！",       // 中文感叹号
                "？",       // 中文问号
                "；",       // 中文分号
                "，",       // 中文逗号
                " ",        // 空格
                ""          // 字符级兜底
        );

        com.study.ai.langchain4j.splitter.RecursiveCharacterTextSplitter splitter =
                new com.study.ai.langchain4j.splitter.RecursiveCharacterTextSplitter(chunkSize, 0, chineseSeparators);

        List<String> chunks = splitter.splitText(text);

        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", chunks.size());
        result.put("splitter", "RecursiveCharacterTextSplitter (Chinese-optimized)");
        result.put("params", Map.of("chunkSize", chunkSize));
        result.put("chunks", chunks);
        return result;
    }
}
