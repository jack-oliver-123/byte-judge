package com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Draft-row writes. Revision is always advanced by the database expression, never read-modify-write. */
@Mapper
public interface ProblemDraftMapper {

    @Insert("""
            INSERT INTO problem_draft
                (id, problemId, revision, title, statement, inputDescription, outputDescription,
                 difficulty, tags, timeLimitCpuMs, timeLimitWallMs, memoryLimitMiB,
                 isDelete, createdById, updatedById)
            VALUES
                (#{id}, #{problemId}, 0, #{content.title}, #{content.statement},
                 #{content.inputDescription}, #{content.outputDescription}, #{difficulty}, #{tags},
                 #{content.timeLimitCpuMs}, #{content.timeLimitWallMs}, #{content.memoryLimitMiB},
                 0, #{operatorId}, #{operatorId})
            """)
    int insert(@Param("id") long id, @Param("problemId") long problemId,
               @Param("content") com.jack.bytejudge.problem.admin.domain.model.DraftContent content,
               @Param("difficulty") String difficulty, @Param("tags") String tags,
               @Param("operatorId") long operatorId);

    @Update("""
            UPDATE problem_draft
            SET revision = revision + 1,
                title = #{content.title},
                statement = #{content.statement},
                inputDescription = #{content.inputDescription},
                outputDescription = #{content.outputDescription},
                difficulty = #{difficulty},
                tags = #{tags},
                timeLimitCpuMs = #{content.timeLimitCpuMs},
                timeLimitWallMs = #{content.timeLimitWallMs},
                memoryLimitMiB = #{content.memoryLimitMiB},
                updatedById = #{operatorId},
                updatedAt = UTC_TIMESTAMP(3)
            WHERE id = #{draftId}
              AND problemId = #{problemId}
              AND revision = #{expectedRevision}
              AND isDelete = 0
            """)
    int updateContent(@Param("problemId") long problemId, @Param("draftId") long draftId,
                      @Param("expectedRevision") long expectedRevision,
                      @Param("content") com.jack.bytejudge.problem.admin.domain.model.DraftContent content,
                      @Param("difficulty") String difficulty, @Param("tags") String tags,
                      @Param("operatorId") long operatorId);

    @Update("""
            UPDATE problem_draft
            SET revision = revision + 1, updatedAt = UTC_TIMESTAMP(3)
            WHERE id = #{draftId} AND revision = #{expectedRevision} AND isDelete = 0
            """)
    int advanceRevision(@Param("draftId") long draftId,
                        @Param("expectedRevision") long expectedRevision);

    @Update("""
            UPDATE problem_draft
            SET isDelete = 1, updatedById = #{operatorId}, updatedAt = UTC_TIMESTAMP(3)
            WHERE id = #{draftId} AND problemId = #{problemId} AND isDelete = 0
            """)
    int markDeleted(@Param("problemId") long problemId, @Param("draftId") long draftId,
                    @Param("operatorId") long operatorId);
}
