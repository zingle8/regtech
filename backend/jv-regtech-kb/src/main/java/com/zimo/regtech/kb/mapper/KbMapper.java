package com.zimo.regtech.kb.mapper;

import com.zimo.regtech.kb.entity.ArticleLog;
import com.zimo.regtech.kb.entity.Dataset;
import com.zimo.regtech.kb.entity.DiffRecord;
import com.zimo.regtech.kb.entity.KbChunk;
import com.zimo.regtech.kb.entity.KbDocument;
import com.zimo.regtech.kb.entity.LegalArticle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbMapper {

    // ---------------------------------------------------------------- 数据集

    List<Dataset> selectDatasets(@Param("keyword") String keyword);

    Dataset selectDataset(@Param("id") Long id);

    long countDatasets();

    int insertDataset(Dataset dataset);

    int updateDataset(Dataset dataset);

    int deleteDataset(@Param("id") Long id);

    int refreshDatasetStats(@Param("id") Long id);

    // ---------------------------------------------------------------- 文档

    long countDocuments(@Param("datasetId") Long datasetId, @Param("keyword") String keyword);

    List<KbDocument> selectDocuments(@Param("datasetId") Long datasetId,
                                     @Param("keyword") String keyword,
                                     @Param("offset") long offset,
                                     @Param("limit") long limit);

    KbDocument selectDocument(@Param("id") Long id);

    int insertDocument(KbDocument document);

    int deleteDocument(@Param("id") Long id);

    int updateDocumentChunkCount(@Param("id") Long id, @Param("count") int count);

    // ---------------------------------------------------------------- 分块

    int insertChunk(@Param("chunk") KbChunk chunk, @Param("embedding") String embedding);

    int deleteChunksByDocument(@Param("documentId") Long documentId);

    long countChunks();

    List<KbChunk> searchChunksByVector(@Param("vector") String vector,
                                       @Param("datasetId") Long datasetId,
                                       @Param("limit") int limit);

    List<KbChunk> searchChunksByKeyword(@Param("keyword") String keyword,
                                        @Param("datasetId") Long datasetId,
                                        @Param("limit") int limit);

    /** 中文按 3-gram 拆词后 OR 匹配，解决整句 ILIKE 命中不到的问题 */
    List<KbChunk> searchChunksByTerms(@Param("terms") List<String> terms,
                                      @Param("termCount") int termCount,
                                      @Param("datasetId") Long datasetId,
                                      @Param("limit") int limit);

    List<KbChunk> selectChunksMissingEmbedding(@Param("limit") int limit);

    int updateChunkEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    List<LegalArticle> selectArticlesMissingEmbedding(@Param("limit") int limit);

    int updateArticleEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    // ---------------------------------------------------------------- 法条

    long countArticles(@Param("keyword") String keyword, @Param("jurisdiction") String jurisdiction);

    List<LegalArticle> selectArticles(@Param("keyword") String keyword,
                                      @Param("jurisdiction") String jurisdiction,
                                      @Param("offset") long offset,
                                      @Param("limit") long limit);

    LegalArticle selectArticle(@Param("id") Long id);

    int insertArticle(@Param("article") LegalArticle article, @Param("embedding") String embedding);

    int updateArticle(@Param("article") LegalArticle article, @Param("embedding") String embedding);

    int deleteArticle(@Param("id") Long id);

    List<LegalArticle> searchArticlesByVector(@Param("vector") String vector, @Param("limit") int limit);

    List<LegalArticle> searchArticlesByKeyword(@Param("keyword") String keyword,
                                               @Param("jurisdiction") String jurisdiction,
                                               @Param("limit") int limit);

    List<Map<String, Object>> countArticlesByJurisdiction();

    /** 指定法域内按向量召回条文（多地域对比用） */
    List<LegalArticle> searchArticlesByVectorInJurisdiction(@Param("vector") String vector,
                                                            @Param("jurisdiction") String jurisdiction,
                                                            @Param("limit") int limit);

    /** 两条条文的语义相似度（1 - 余弦距离），任一缺向量时返回 null */
    Double similarityBetween(@Param("leftId") Long leftId, @Param("rightId") Long rightId);

    /** 条文的知识库出处：数据集 → 文档 → 条文库 */
    List<Map<String, Object>> selectArticleCitations(@Param("ids") List<Long> ids);

    // ---------------------------------------------------------------- 条文变更日志

    int insertArticleLog(ArticleLog articleLog);

    List<ArticleLog> selectArticleLogs(@Param("limit") int limit);

    // ---------------------------------------------------------------- 差异

    int insertDiff(DiffRecord record);

    long countDiffs();

    List<DiffRecord> selectDiffs(@Param("offset") long offset, @Param("limit") long limit);

    DiffRecord selectDiff(@Param("id") Long id);

    int deleteDiff(@Param("id") Long id);
}
