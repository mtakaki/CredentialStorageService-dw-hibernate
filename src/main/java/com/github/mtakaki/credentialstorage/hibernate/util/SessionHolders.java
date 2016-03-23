package com.github.mtakaki.credentialstorage.hibernate.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mtakaki.credentialstorage.hibernate.UnitOfWorkAspect;

import io.dropwizard.db.ManagedDataSource;

import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class SessionHolders {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionHolders.class);

    private final Set<UnitOfWorkAspect> unitOfWorks = Collections.synchronizedSet(new HashSet<>());
    private final SessionFactory oldSessionFactory;
    private final ManagedDataSource oldDataSource;

    @Setter
    private boolean closeSession = false;

    public void add(final UnitOfWorkAspect unitOfWork) {
        this.unitOfWorks.add(unitOfWork);
    }

    //TODO This may need to be synchronized. Needs more tests.
    public synchronized void remove(final UnitOfWorkAspect unitOfWork) {
        this.unitOfWorks.remove(unitOfWork);

        if (this.closeSession && this.unitOfWorks.isEmpty()) {
            this.closeConnections();
        }
    }

    public void closeConnections() {
        this.oldSessionFactory.close();
        try {
            this.oldDataSource.stop();
        } catch (final Exception e) {
            LOGGER.error(
                    "Failed to close database connections. The application is going to leak connections.",
                    e);
        }
    }

    public boolean isEmpty() {
        return this.unitOfWorks.isEmpty();
    }
}