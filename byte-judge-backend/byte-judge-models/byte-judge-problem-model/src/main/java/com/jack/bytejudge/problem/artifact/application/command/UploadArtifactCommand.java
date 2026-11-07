package com.jack.bytejudge.problem.artifact.application.command;

import java.io.InputStream;

/**
 * One upload as accepted at the protocol boundary. The stream is consumed synchronously and is
 * never retained; the caller closes it. Raw metadata is parsed and range-checked inside the use
 * case so rejected uploads can be audited.
 */
public record UploadArtifactCommand(long problemId, long expectedRevision, String kind,
                                    int caseOrder, int displayOrder, long declaredSize,
                                    String fileName, InputStream content,
                                    long operatorId, String operatorAccount, String requestId) {
}
