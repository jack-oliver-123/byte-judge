package com.jack.bytejudge.problem.artifact.interfaces.http.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart fields as received. It is a protocol object only: no domain type and no entity is
 * exposed to the HTTP layer.
 */
public record UploadArtifactRequest(MultipartFile file, String kind, int caseOrder,
                                    int displayOrder, long expectedRevision) {
}
