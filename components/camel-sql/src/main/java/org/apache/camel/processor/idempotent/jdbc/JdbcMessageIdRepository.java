/**
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
package org.apache.camel.processor.idempotent.jdbc;

import javax.sql.DataSource;

import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version $Revision$
 */
@ManagedResource("JdbcMessageIdRepository")
public class JdbcMessageIdRepository extends ServiceSupport implements IdempotentRepository<String> {
    
    protected static final String QUERY_STRING = "SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?";
    protected static final String INSERT_STRING = "INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId) VALUES (?, ?)";
    protected static final String DELETE_STRING = "DELETE FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?";
    
    private final JdbcTemplate jdbcTemplate;
    private final String processorName;
    private final TransactionTemplate transactionTemplate;

    public JdbcMessageIdRepository(DataSource dataSource, String processorName) {
        this(dataSource, createTransactionTemplate(dataSource), processorName);
    }

    public JdbcMessageIdRepository(DataSource dataSource, TransactionTemplate transactionTemplate, String processorName) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.afterPropertiesSet();
        this.processorName = processorName;
        this.transactionTemplate = transactionTemplate;
    }

    public static JdbcMessageIdRepository jpaMessageIdRepository(DataSource dataSource, String processorName) {
        return new JdbcMessageIdRepository(dataSource, processorName);
    }

    private static TransactionTemplate createTransactionTemplate(DataSource dataSource) {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate;
    }

    @ManagedOperation(description = "Adds the key to the store")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean add(final String messageId) {
        // Run this in single transaction.
        Boolean rc = (Boolean)transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus status) {
                int count = jdbcTemplate.queryForInt(QUERY_STRING, processorName, messageId);
                if (count == 0) {
                    jdbcTemplate.update(INSERT_STRING, processorName, messageId);
                    return Boolean.TRUE;
                } else {
                    return Boolean.FALSE;
                }
            }
        });
        return rc.booleanValue();
    }

    @ManagedOperation(description = "Does the store contain the given key")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean contains(final String messageId) {
        // Run this in single transaction.
        Boolean rc = (Boolean)transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus status) {
                int count = jdbcTemplate.queryForInt(QUERY_STRING, processorName, messageId);
                if (count == 0) {
                    return Boolean.FALSE;
                } else {
                    return Boolean.TRUE;
                }
            }
        });
        return rc.booleanValue();
    }

    @ManagedOperation(description = "Remove the key from the store")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean remove(final String messageId) {
        Boolean rc = (Boolean)transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus status) {
                int updateCount = jdbcTemplate.update(DELETE_STRING, processorName, messageId);
                if (updateCount == 0) {
                    return Boolean.FALSE;
                } else {
                    return Boolean.TRUE;
                }
            }
        });
        return rc.booleanValue();
    }

    public boolean confirm(String s) {
        // noop
        return true;
    }

    @ManagedAttribute(description = "The processor name")
    public String getProcessorName() {
        return processorName;
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
    }
}
