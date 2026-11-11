package com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper;

import com.jack.bytejudge.problem.admin.infrastructure.persistence.entity.LockedDraftRow;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.entity.ProblemDetailRow;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.entity.ProblemListItemRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Select(ProblemSql.DETAIL)
    ProblemDetailRow findDetail(@Param("problemId") long problemId);

    @Select(ProblemSql.LOCKED_DETAIL)
    ProblemDetailRow lockDetail(@Param("problemId") long problemId);

    @Select(ProblemSql.LIST_PAGE)
    List<ProblemListItemRow> findPage(@Param("title") String title,
                                      @Param("difficulty") String difficulty,
                                      @Param("includeDeleted") boolean includeDeleted,
                                      @Param("offset") long offset,
                                      @Param("limit") int limit);

    @Select(ProblemSql.LIST_COUNT)
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
}
