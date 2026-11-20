/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.idempotent.jpa;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.persistence.RollbackException;

import org.apache.camel.component.jpa.TransactionStrategy;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two callers add the same message id at the same time: both find no row, and the insert of one fails on the unique
 * key. The repository must answer that the message id is a duplicate (add returns false) whatever the database driver
 * reports and whether the failure comes at the flush or when the transaction commits.
 */
class JpaMessageIdRepositoryConcurrentInsertTest {

    private final EntityManagerFactory emf = mock(EntityManagerFactory.class);
    private final EntityManager em = mock(EntityManager.class);

    @BeforeEach
    void setUp() {
        Query query = mock(Query.class);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        // the other caller has not committed yet: no row
        when(query.getResultList()).thenReturn(List.of());
        when(em.createQuery(anyString())).thenReturn(query);
        when(em.isOpen()).thenReturn(true);
        when(emf.createEntityManager()).thenReturn(em);
    }

    @Test
    void postgresDuplicateKeyIsDuplicate() {
        // a PostgreSQL unique violation: the driver's PSQLException is a plain SQLException
        failFlushWith(new SQLException("duplicate key value violates unique constraint", "23505"));

        assertFalse(repository(Runnable::run).add("1"));
    }

    @Test
    void sqlServerDuplicateKeyIsDuplicate() {
        failFlushWith(new SQLException("Violation of UNIQUE KEY constraint", "23000", 2627));

        assertFalse(repository(Runnable::run).add("1"));
    }

    @Test
    void integrityConstraintViolationExceptionIsDuplicate() {
        // drivers such as the H2, Derby, MySQL and Oracle ones throw this subclass
        failFlushWith(new SQLIntegrityConstraintViolationException("Unique index or primary key violation", "23505"));

        assertFalse(repository(Runnable::run).add("1"));
    }

    @Test
    void duplicateWhenTheTransactionCannotCommit() {
        failFlushWith(new SQLIntegrityConstraintViolationException("Unique index or primary key violation", "23505"));
        // the failed flush marked the transaction for rollback (a shared EntityManager of the transaction), so the
        // commit fails
        TransactionStrategy commitFails = runnable -> {
            runnable.run();
            throw new RollbackException("Transaction was marked for rollback only");
        };

        assertFalse(repository(commitFails).add("1"));
    }

    @Test
    void otherDatabaseFailureFails() {
        doThrow(new JDBCConnectionException(
                "could not execute statement", new SQLException("Connection reset", "08006"))).when(em).flush();

        assertThrows(PersistenceException.class, () -> repository(Runnable::run).add("1"));
    }

    @Test
    void otherCommitFailureFails() {
        TransactionStrategy commitFails = runnable -> {
            runnable.run();
            throw new RollbackException("Commit failed");
        };

        assertThrows(RollbackException.class, () -> repository(commitFails).add("1"));
    }

    @Test
    void newMessageIdIsAdded() {
        assertTrue(repository(Runnable::run).add("1"));
    }

    private void failFlushWith(SQLException sqlException) {
        // Hibernate 7 throws its JDBCException subclass (a PersistenceException) with the driver's exception as cause
        doThrow(new ConstraintViolationException("could not execute statement", sqlException, "uk_processor_message"))
                .when(em).flush();
    }

    private JpaMessageIdRepository repository(TransactionStrategy transactionStrategy) {
        return new JpaMessageIdRepository(emf, transactionStrategy, "myProcessor");
    }
}
