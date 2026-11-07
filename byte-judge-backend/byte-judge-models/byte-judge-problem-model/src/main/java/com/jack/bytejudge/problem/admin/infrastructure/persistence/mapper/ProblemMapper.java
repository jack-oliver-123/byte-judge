package com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Identity and read-side access for problems. Content writes live in
 * {@link ProblemDraftMapper}; both are bound under the admin feature's repository adapter.
 */
@Mapper
public interface ProblemMapper {

    @Select("SELECT nextValue FROM problem_no_sequence WHERE id = 1 FOR UPDATE")
    Integer lockNextProblemNo();

    @Update("UPDATE problem_no_sequence SET nextValue = nextValue + 1 WHERE id = 1")
    int advanceProblemNo();

    @Insert("""
            INSERT INTO problem (id, problemNo, status, everPublished, createdById, isDelete)
            VALUES (#{id}, #{problemNo}, 'DRAFT', 0, #{createdById}, 0)
            """)
    int insertProblem(@Param("id") long id, @Param("problemNo") int problemNo,
                      @Param("createdById") long createdById);

    @Update("UPDATE problem SET currentDraftId = #{draftId} WHERE id = #{problemId}")
    int attachDraft(@Param("problemId") long problemId, @Param("draftId") long draftId);

    @Update("UPDATE problem SET updatedAt = UTC_TIMESTAMP(3) WHERE id = #{problemId}")
    int touch(@Param("problemId") long problemId);

    @Update("""
            UPDATE problem
            SET status = 'DELETED', isDelete = 1, updatedAt = UTC_TIMESTAMP(3)
            WHERE id = #{problemId} AND status = 'DRAFT' AND isDelete = 0
            """)
    int markDeleted(@Param("problemId") long problemId);

    @Select("""
            SELECT p.id AS problemId, p.problemNo, p.status, p.everPublished, p.createdById,
                   p.isDelete AS problemDeleted, p.createdAt AS createdAt,
                   d.id AS draftId, d.problemId AS draftProblemId, d.revision,
                   d.title, d.statement, d.inputDescription, d.outputDescription, d.difficulty,
                   d.tags, d.timeLimitCpuMs, d.timeLimitWallMs, d.memoryLimitMiB,
                   d.isDelete AS draftDeleted, d.createdById AS draftCreatedById,
                   d.updatedById AS draftUpdatedById, d.createdAt AS draftCreatedAt,
                   d.updatedAt AS updatedAt
            FROM problem p
            JOIN problem_draft d ON d.problemId = p.id
            WHERE p.id = #{problemId}
            """)
    ProblemDetailRow findDetail(@Param("problemId") long problemId);

    @Select("""
            SELECT p.id AS problemId, p.problemNo, p.status, p.everPublished, p.createdById,
                   p.isDelete AS problemDeleted, p.createdAt AS createdAt,
                   d.id AS draftId, d.problemId AS draftProblemId, d.revision,
                   d.title, d.statement, d.inputDescription, d.outputDescription, d.difficulty,
                   d.tags, d.timeLimitCpuMs, d.timeLimitWallMs, d.memoryLimitMiB,
                   d.isDelete AS draftDeleted, d.createdById AS draftCreatedById,
                   d.updatedById AS draftUpdatedById, d.createdAt AS draftCreatedAt,
                   d.updatedAt AS updatedAt
            FROM problem p
            JOIN problem_draft d ON d.problemId = p.id
            WHERE p.id = #{problemId}
            FOR UPDATE
            """)
    ProblemDetailRow lockDetail(@Param("problemId") long problemId);

    @Select("""
            <script>
            SELECT p.id AS problemId, p.problemNo, d.title, d.difficulty, p.status, d.revision,
                   p.createdAt AS createdAt, d.updatedAt AS updatedAt
            FROM problem p
            JOIN problem_draft d ON d.problemId = p.id
            <where>
              <if test="includeDeleted == false">
                AND p.isDelete = 0 AND d.isDelete = 0 AND p.status = 'DRAFT'
              </if>
              <if test="title != null">
                AND d.title LIKE #{title} ESCAPE '!'
              </if>
              <if test="difficulty != null">
                AND d.difficulty = #{difficulty}
              </if>
            </where>
            ORDER BY p.createdAt DESC, p.problemNo DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ProblemListItemRow> findPage(@Param("title") String title,
                                      @Param("difficulty") String difficulty,
                                      @Param("includeDeleted") boolean includeDeleted,
                                      @Param("offset") long offset,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM problem p
            JOIN problem_draft d ON d.problemId = p.id
            <where>
              <if test="includeDeleted == false">
                AND p.isDelete = 0 AND d.isDelete = 0 AND p.status = 'DRAFT'
              </if>
              <if test="title != null">
                AND d.title LIKE #{title} ESCAPE '!'
              </if>
              <if test="difficulty != null">
                AND d.difficulty = #{difficulty}
              </if>
            </where>
            </script>
            """)
    long count(@Param("title") String title,
               @Param("difficulty") String difficulty,
               @Param("includeDeleted") boolean includeDeleted);

    @Select("""
            SELECT d.id AS draftId, d.problemId AS problemId, d.revision AS revision,
                   p.createdById AS createdById
            FROM problem p
            JOIN problem_draft d ON d.problemId = p.id
            WHERE p.id = #{problemId}
              AND p.isDelete = 0
              AND p.status = 'DRAFT'
              AND d.isDelete = 0
            FOR UPDATE
            """)
    LockedDraftRow lockLiveDraft(@Param("problemId") long problemId);

    record ProblemDetailRow(long problemId, int problemNo, String status, boolean everPublished,
                            long createdById, boolean problemDeleted, LocalDateTime createdAt,
                            long draftId, long draftProblemId, long revision, String title,
                            String statement, String inputDescription, String outputDescription,
                            String difficulty, String tags, Integer timeLimitCpuMs,
                            Integer timeLimitWallMs, Integer memoryLimitMiB,
                            boolean draftDeleted, long draftCreatedById, long draftUpdatedById,
                            LocalDateTime draftCreatedAt, LocalDateTime updatedAt) {
    }

    record ProblemListItemRow(long problemId, int problemNo, String title, String difficulty,
                              String status, long revision, LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
    }

    record LockedDraftRow(long draftId, long problemId, long revision, long createdById) {
    }
}
