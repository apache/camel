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
package org.apache.camel.processor.aggregate.jdbc;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultJdbcOptimisticLockingExceptionMapperTest {

    private final DefaultJdbcOptimisticLockingExceptionMapper mapper = new DefaultJdbcOptimisticLockingExceptionMapper();

    @Test
    public void testSQLExceptionWithoutSQLState() {
        // a connection pool timeout has no SQLState
        SQLException cause = new SQLTransientConnectionException("Connection is not available, request timed out");

        assertFalse(DefaultJdbcOptimisticLockingExceptionMapper.isConstraintViolation(cause));
        assertFalse(mapper.isOptimisticLocking(new CannotCreateTransactionException("Could not open connection", cause)));
    }

    @Test
    public void testSQLExceptionWithoutSQLStateAndConfiguredClassName() {
        mapper.addClassName(VendorLockException.class.getName());

        Exception cause = new VendorLockException(new SQLException("Lock conflict"));

        assertTrue(mapper.isOptimisticLocking(cause));
    }

    @Test
    public void testSQLExceptionWithSQLState() {
        assertTrue(mapper.isOptimisticLocking(new RuntimeException(new SQLException("Duplicate key", "23505"))));
        assertFalse(mapper.isOptimisticLocking(new RuntimeException(new SQLException("Connection refused", "08001"))));
    }

    private static class VendorLockException extends Exception {

        VendorLockException(Throwable cause) {
            super(cause);
        }
    }
}
