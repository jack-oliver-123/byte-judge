package com.jack.bytejudge.problem.artifact.infrastructure.persistence;

import com.jack.bytejudge.problem.artifact.application.port.ArtifactUnitOfWork;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Wraps the manifest/revision/audit writes in one database transaction. */
public final class SpringArtifactUnitOfWork implements ArtifactUnitOfWork {
    private final TransactionTemplate transactions;

    public SpringArtifactUnitOfWork(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
