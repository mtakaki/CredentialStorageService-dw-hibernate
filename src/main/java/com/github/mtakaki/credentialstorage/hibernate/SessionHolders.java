package com.github.mtakaki.credentialstorage.hibernate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.db.ManagedDataSource;

import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Holds the current active {@link SessionFactory} and {@link ManagedDataSource}
 * . Once the connection is set to be closed, the last user of the connection
 * will close it.
 *
 * @author mtakaki
 *
 */
@RequiredArgsConstructor
class SessionHolders {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionHolders.class);

    private final Set<UnitOfWorkAspect> unitOfWorks = Collections.synchronizedSet(new HashSet<>());
    private final SessionFactory sessionFactory;
    private final ManagedDataSource dataSource;

    @Setter
    private boolean closeSession = false;

    /**
     * Adds a {@link UnitOfWorkAspect} to the list of users of the connection.
     *
     * @param unitOfWork
     *            The unit of work that is using the database connection.
     */
    public void add(final UnitOfWorkAspect unitOfWork) {
        this.unitOfWorks.add(unitOfWork);
    }

    /**
     * Removes the {@link UnitOfWorkAspect} from the list of users. If it's the
     * last unit of work using the connection and there is a new connection, the
     * connection is closed.
     *
     * @param unitOfWork
     *            The unit of work that was using the database connection.
     */
    public synchronized void remove(final UnitOfWorkAspect unitOfWork) {
        this.unitOfWorks.remove(unitOfWork);

        if (this.closeSession && this.unitOfWorks.isEmpty()) {
            this.closeConnections();
        }
    }

    /**
     * Closes the current database connection. It suppress exceptions if the
     * internal {@link ManagedDataSource} fails to stop, but it will be logged.
     */
    public void closeConnections() {
        this.sessionFactory.close();
        try {
            this.dataSource.stop();
        } catch (final Exception e) {
            LOGGER.error(
                    "Failed to close database connections. The application is going to leak connections.",
                    e);
        }
    }

    /**
     * Verifies if there is no {@link UnitOfWorkAspect} using the connection.
     *
     * @return {@code true} if there is no {@link UnitOfWorkAspect} using the
     *         connection, {@code false} if otherwise.
     */
    public boolean isEmpty() {
        return this.unitOfWorks.isEmpty();
    }
}