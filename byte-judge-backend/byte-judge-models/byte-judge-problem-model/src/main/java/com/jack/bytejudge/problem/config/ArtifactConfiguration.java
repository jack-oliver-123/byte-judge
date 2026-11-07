package com.jack.bytejudge.problem.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.problem.admin.application.port.ProblemAudit;
import com.jack.bytejudge.problem.artifact.application.ArtifactCatalog;
import com.jack.bytejudge.problem.artifact.application.ArtifactRegistrationService;
import com.jack.bytejudge.problem.artifact.application.port.ArtifactStore;
import com.jack.bytejudge.problem.artifact.application.port.ArtifactUnitOfWork;
import com.jack.bytejudge.problem.artifact.application.port.DraftArtifactSeam;
import com.jack.bytejudge.problem.artifact.domain.repository.ArtifactManifestRepository;
import com.jack.bytejudge.problem.artifact.infrastructure.persistence.SpringArtifactUnitOfWork;
import com.jack.bytejudge.problem.artifact.infrastructure.persistence.mapper.ProblemArtifactMapper;
import com.jack.bytejudge.problem.artifact.infrastructure.persistence.repository.MybatisArtifactManifestRepository;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Assembly for the artifact feature: persistence adapters, the draft seam and the use cases. */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = ProblemArtifactMapper.class)
public class ArtifactConfiguration {

    @Bean
    ArtifactManifestRepository artifactManifestRepository(ProblemArtifactMapper mapper) {
        return new MybatisArtifactManifestRepository(mapper);
    }

    @Bean
    ArtifactUnitOfWork artifactUnitOfWork(PlatformTransactionManager transactions) {
        return new SpringArtifactUnitOfWork(new TransactionTemplate(transactions));
    }

    @Bean
    ArtifactRegistrationService artifactRegistrationService(ArtifactStore store,
                                                            ArtifactManifestRepository manifests,
                                                            DraftArtifactSeam drafts, ProblemAudit audit,
                                                            IdentifierGenerator identifiers,
                                                            ArtifactUnitOfWork unitOfWork) {
        return new ArtifactRegistrationService(store, manifests, drafts, audit, identifiers, unitOfWork);
    }

    @Bean
    ArtifactCatalog artifactCatalog(ArtifactManifestRepository manifests) {
        return new ArtifactCatalog(manifests);
    }
}
