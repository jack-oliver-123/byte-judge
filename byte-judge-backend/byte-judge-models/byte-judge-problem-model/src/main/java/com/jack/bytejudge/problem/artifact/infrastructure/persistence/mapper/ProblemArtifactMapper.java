package com.jack.bytejudge.problem.artifact.infrastructure.persistence.mapper;

import com.jack.bytejudge.problem.artifact.infrastructure.persistence.entity.ArtifactManifestEntity;
import com.jack.bytejudge.problem.artifact.infrastructure.persistence.entity.ProblemArtifactEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Manifest persistence. Stored kinds and family grouping mirror the domain enum; the database
 * cannot call {@code ArtifactKind}, so the CASE expressions repeat the SAMPLE/HIDDEN split that
 * {@code ArtifactKind.family()} defines. V3's check constraint keeps the stored names aligned.
 */
@Mapper
public interface ProblemArtifactMapper {

    @Select("""
            SELECT COUNT(*) FROM problem_artifact
            WHERE draftId = #{draftId} AND kind = #{kind} AND caseOrder = #{caseOrder} AND isDelete = 0
            """)
    int countLiveSlot(@Param("draftId") long draftId, @Param("kind") String kind,
                      @Param("caseOrder") int caseOrder);

    /**
     * Sample input/output share one group and hidden input/output share another; the cap counts
     * those pairs, not individual files. {@code family} is HIDDEN or SAMPLE.
     */
    @Select("""
            SELECT COUNT(*) FROM problem_artifact
            WHERE draftId = #{draftId} AND caseOrder = #{caseOrder} AND isDelete = 0
              AND (CASE WHEN kind IN ('HIDDEN_INPUT', 'HIDDEN_OUTPUT') THEN 'HIDDEN' ELSE 'SAMPLE' END)
                  = #{family}
            """)
    int countLiveGroup(@Param("draftId") long draftId, @Param("family") String family,
                       @Param("caseOrder") int caseOrder);

    @Select("""
            SELECT COUNT(*) FROM (
                SELECT DISTINCT
                    (CASE WHEN kind IN ('HIDDEN_INPUT', 'HIDDEN_OUTPUT') THEN 'HIDDEN' ELSE 'SAMPLE' END)
                        AS family,
                    caseOrder
                FROM problem_artifact
                WHERE draftId = #{draftId} AND isDelete = 0
            ) live_groups
            """)
    int countLiveGroups(@Param("draftId") long draftId);

    /**
     * A duplicate key is surfaced so the adapter can turn a lost concurrent slot race into the
     * same explicit rejection as the pre-check, never a silent replacement.
     */
    @Insert("""
            INSERT INTO problem_artifact
                (id, problemId, draftId, contentDigest, byteSize, fileName, kind, caseOrder, displayOrder)
            VALUES
                (#{artifact.id}, #{artifact.problemId}, #{artifact.draftId}, #{artifact.contentDigest},
                 #{artifact.byteSize}, #{artifact.fileName}, #{artifact.kind},
                 #{artifact.caseOrder}, #{artifact.displayOrder})
            """)
    int insert(@Param("artifact") ProblemArtifactEntity artifact);

    /**
     * Ordered by kind first so the four test-data groups stay together in the detail view; the
     * identifier breaks any remaining tie deterministically.
     */
    @Select("""
            SELECT id, contentDigest, byteSize, fileName, kind, caseOrder, displayOrder
            FROM problem_artifact
            WHERE problemId = #{problemId} AND isDelete = 0
            ORDER BY kind, caseOrder, displayOrder, id
            """)
    List<ArtifactManifestEntity> findLiveByProblemId(@Param("problemId") long problemId);
}
