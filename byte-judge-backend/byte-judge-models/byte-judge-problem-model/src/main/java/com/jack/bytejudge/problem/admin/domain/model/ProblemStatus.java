package com.jack.bytejudge.problem.admin.domain.model;

/** Stable problem state stored in {@code problem.status}. Publication states arrive in a later slice. */
public enum ProblemStatus {
    DRAFT,
    DELETED;

    public static ProblemStatus fromColumn(String value) {
        return value == null ? null : valueOf(value);
    }
}
