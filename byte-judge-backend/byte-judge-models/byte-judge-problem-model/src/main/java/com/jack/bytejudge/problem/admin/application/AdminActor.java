package com.jack.bytejudge.problem.admin.application;

/** Minimal authenticated actor for admin writes; no session or credential data travels inward. */
public record AdminActor(long userId, String account) {
}
