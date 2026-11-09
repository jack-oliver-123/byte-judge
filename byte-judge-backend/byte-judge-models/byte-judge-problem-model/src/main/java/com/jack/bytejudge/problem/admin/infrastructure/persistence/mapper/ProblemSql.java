package com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper;

/**
 * Shared read-side SQL. Keeping the projection and the list predicate in one place makes the
 * detail/lock and page/count pairs stay byte-for-byte identical as the filters evolve.
 */
final class ProblemSql {
    private ProblemSql() {
    }

    /** Detail projection plus its single-row predicate; locked reads append {@code FOR UPDATE}. */
    static final String DETAIL = """
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
            """;

    static final String LOCKED_DETAIL = DETAIL + "FOR UPDATE";

    /** The management list filters, shared by the page query and the matching count query. */
    static final String LIST_FILTER = """
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
            """;

    static final String LIST_PAGE = "<script>"
            + "SELECT p.id AS problemId, p.problemNo, d.title, d.difficulty, p.status, d.revision, "
            + "p.createdAt AS createdAt, d.updatedAt AS updatedAt "
            + "FROM problem p JOIN problem_draft d ON d.problemId = p.id "
            + LIST_FILTER
            + "ORDER BY p.createdAt DESC, p.problemNo DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>";

    static final String LIST_COUNT = "<script>"
            + "SELECT COUNT(*) FROM problem p JOIN problem_draft d ON d.problemId = p.id "
            + LIST_FILTER
            + "</script>";
}
