package com.jack.bytejudge.problem.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.problem.admin.application.DraftService;
import com.jack.bytejudge.problem.admin.application.port.ProblemAudit;
import com.jack.bytejudge.problem.admin.domain.repository.ProblemDraftRepository;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper.ProblemDraftMapper;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper.ProblemMapper;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.repository.MybatisProblemDraftRepository;
import com.jack.bytejudge.problem.artifact.application.ArtifactCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembly for the draft lifecycle. Mapper scanning is owned by
 * {@link ProblemSecurityConfiguration} for the shared admin persistence package, so this class
 * only binds the domain port and the use case.
 */
@Configuration(proxyBeanMethods = false)
public class ProblemDraftConfiguration {

    @Bean
    ProblemDraftRepository problemDraftRepository(ProblemMapper problems, ProblemDraftMapper drafts,
                                                  IdentifierGenerator identifiers) {
        return new MybatisProblemDraftRepository(problems, drafts, identifiers);
    }

    /** The draft lifecycle owns the locking and revision rules used by artifact registration. */
    @Bean
    DraftService draftService(ProblemDraftRepository drafts, ProblemAudit audit,
                              ArtifactCatalog artifacts) {
        return new DraftService(drafts, audit, artifacts);
    }
}
