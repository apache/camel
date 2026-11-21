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
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Two callers add the same message id at the same time: both find no row, and the insert of one fails on the unique
 * key. The repository must answer that the message id is a duplicate (add returns false) whatever the database driver
 * reports and whether the failure comes at the flush or when the transaction commits. As a constraint violation can
 * also be on another constraint (such as a primary key collision when the id sequence is behind the table), the
 * repository reads the message id again with a new EntityManager in a new transaction, and fails when it is not there.
 */
class JpaMessageIdRepositoryConcurrentInsertTest {

    private final EntityManagerFactory emf = mock(EntityManagerFactory.class);
    // the EntityManager of the insert
    private final EntityManager em = mock(EntityManager.class);
    // the EntityManager that reads the message id again after a constraint violation
    private final EntityManager reread = mock(EntityManager.class);
    private final Transactions transactions = new Transactions();
    // the transaction in which the message id was read again (0: not read again)
    private int rereadInTransaction;

    @BeforeEach
    void setUp() {
        // the other caller has not committed yet when the insert reads: no row
        Query query = mock(Query.class);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(em.createQuery(anyString())).thenReturn(query);
        when(em.isOpen()).thenReturn(true);
        when(reread.isOpen()).thenReturn(true);
        when(emf.createEntityManager()).thenReturn(em, reread);
    }

    @Test
    void postgresDuplicateKeyIsDuplicate() {
        // a PostgreSQL unique violation: the driver's PSQLException is a plain SQLException
        failFlushWith(new SQLException("duplicate key value violates unique constraint", "23505"));
        storedByOtherCaller(true);

        assertFalse(repository(transactions).add("1"));
        // read again with a new EntityManager, in a transaction of its own after the one of the insert
        verify(emf, times(2)).createEntityManager();
        assertEquals(2, rereadInTransaction);
        assertFalse(transactions.nested);
    }

    @Test
    void sqlServerDuplicateKeyIsDuplicate() {
        failFlushWith(new SQLException("Violation of UNIQUE KEY constraint", "23000", 2627));
        storedByOtherCaller(true);

        assertFalse(repository(transactions).add("1"));
        assertEquals(2, rereadInTransaction);
    }

    @Test
    void integrityConstraintViolationExceptionIsDuplicate() {
        // drivers such as the H2, Derby, MySQL and Oracle ones throw this subclass
        failFlushWith(new SQLIntegrityConstraintViolationException("Unique index or primary key violation", "23505"));
        storedByOtherCaller(true);

        assertFalse(repository(transactions).add("1"));
        assertEquals(2, rereadInTransaction);
    }

    @Test
    void duplicateWhenTheTransactionCannotCommit() {
        failFlushWith(new SQLIntegrityConstraintViolationException("Unique index or primary key violation", "23505"));
        storedByOtherCaller(true);
        // the failed flush marked the transaction for rollback (a shared EntityManager of the transaction), so the
        // commit fails: Spring's JpaTransactionManager throws this exception, without a cause
        transactions.firstCommitFailure = markedForRollback();

        assertFalse(repository(transactions).add("1"));
        assertEquals(2, rereadInTransaction);
    }

    @Test
    void primaryKeyCollisionFails() {
        // the id sequence is behind the table: PostgreSQL reports the primary key collision with 23505 as well, and the
        // message id is not in the table
        failFlushWith(new SQLException(
                "duplicate key value violates unique constraint \"camel_messageprocessed_pkey\"",
                "23505"));
        storedByOtherCaller(false);

        PersistenceException e = assertThrows(PersistenceException.class, () -> repository(transactions).add("1"));
        assertInstanceOf(ConstraintViolationException.class, e.getCause());
        assertEquals(2, rereadInTransaction);
    }

    @Test
    void primaryKeyCollisionWhenTheTransactionCannotCommitFails() {
        failFlushWith(new SQLException("Violation of PRIMARY KEY constraint", "23000", 2627));
        storedByOtherCaller(false);
        UnexpectedRollbackException commitFailure = markedForRollback();
        transactions.firstCommitFailure = commitFailure;

        // the constraint violation is thrown, as the commit failure does not say why, and the commit failure is kept
        PersistenceException e = assertThrows(PersistenceException.class, () -> repository(transactions).add("1"));
        assertInstanceOf(ConstraintViolationException.class, e.getCause());
        assertSame(commitFailure, e.getSuppressed()[0]);
        assertEquals(2, rereadInTransaction);
    }

    @Test
    void violationAtCommitIsDuplicate() {
        // the constraint violation comes when the transaction commits (such as a deferred constraint)
        transactions.firstCommitFailure = commitFailsWith(new SQLException("duplicate key value", "23505"));
        storedByOtherCaller(true);

        assertFalse(repository(transactions).add("1"));
        assertEquals(2, rereadInTransaction);
    }

    @Test
    void violationAtCommitNotStoredFails() {
        TransactionSystemException commitFailure = commitFailsWith(new SQLException("duplicate key value", "23505"));
        transactions.firstCommitFailure = commitFailure;
        storedByOtherCaller(false);

        TransactionSystemException e
                = assertThrows(TransactionSystemException.class, () -> repository(transactions).add("1"));
        assertSame(commitFailure, e);
        assertEquals(2, rereadInTransaction);
    }

    @Test
    void readAgainFailureFails() {
        failFlushWith(new SQLException("duplicate key value violates unique constraint", "23505"));
        Query query = mock(Query.class);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenThrow(new JDBCConnectionException(
                "could not execute query", new SQLException("Connection reset", "08006")));
        when(reread.createQuery(anyString())).thenReturn(query);

        // the failure of the read is thrown, with the constraint violation kept
        PersistenceException e = assertThrows(PersistenceException.class, () -> repository(transactions).add("1"));
        assertInstanceOf(JDBCConnectionException.class, e.getCause());
        assertInstanceOf(ConstraintViolationException.class, e.getSuppressed()[0].getCause());
    }

    @Test
    void otherDatabaseFailureFails() {
        doThrow(new JDBCConnectionException(
                "could not execute statement", new SQLException("Connection reset", "08006"))).when(em).flush();

        assertThrows(PersistenceException.class, () -> repository(transactions).add("1"));
        verify(emf, times(1)).createEntityManager();
    }

    @Test
    void otherCommitFailureFails() {
        transactions.firstCommitFailure
                = new TransactionSystemException("Could not commit JPA transaction", new RollbackException("Commit failed"));

        assertThrows(TransactionSystemException.class, () -> repository(transactions).add("1"));
        verify(emf, times(1)).createEntityManager();
    }

    @Test
    void newMessageIdIsAdded() {
        assertTrue(repository(transactions).add("1"));
        verify(emf, times(1)).createEntityManager();
    }

    private void failFlushWith(SQLException sqlException) {
        // Hibernate 7 throws its JDBCException subclass (a PersistenceException) with the driver's exception as cause
        doThrow(new ConstraintViolationException("could not execute statement", sqlException, "uk_processor_message"))
                .when(em).flush();
    }

    private static UnexpectedRollbackException markedForRollback() {
        return new UnexpectedRollbackException("Transaction silently rolled back because it has been marked as rollback-only");
    }

    private static TransactionSystemException commitFailsWith(SQLException sqlException) {
        // a commit failure with the constraint violation in its cause chain (JpaTransactionManager translates it, such
        // as into a JpaSystemException; TransactionSystemException is its fallback)
        return new TransactionSystemException(
                "Could not commit JPA transaction", new RollbackException(
                        "Error while committing the transaction",
                        new ConstraintViolationException("could not execute statement", sqlException, "uk_processor_message")));
    }

    private void storedByOtherCaller(boolean stored) {
        // the read in a new transaction sees the row of the other caller, which has committed by then
        Query query = mock(Query.class);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenAnswer(invocation -> {
            rereadInTransaction = transactions.count;
            return stored ? List.of(new MessageProcessed()) : List.of();
        });
        when(reread.createQuery(anyString())).thenReturn(query);
    }

    private JpaMessageIdRepository repository(TransactionStrategy transactionStrategy) {
        return new JpaMessageIdRepository(emf, transactionStrategy, "myProcessor");
    }

    /**
     * Runs each unit of work as a transaction of its own, and fails the commit of the first one when asked.
     */
    private static final class Transactions implements TransactionStrategy {
        private int count;
        private boolean active;
        private boolean nested;
        private RuntimeException firstCommitFailure;

        @Override
        public void executeInTransaction(Runnable runnable) {
            nested |= active;
            active = true;
            count++;
            try {
                runnable.run();
            } finally {
                active = false;
            }
            if (count == 1 && firstCommitFailure != null) {
                throw firstCommitFailure;
            }
        }
    }
}
