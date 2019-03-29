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
package org.apache.camel.processor.idempotent.jdbc;

import javax.sql.DataSource;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base class for JDBC-based idempotent repositories that allows the schema to be changed.
 * <p/>
 * Subclasses need only implement theses methods:
 * <ul>
 *   <li>{@link #queryForInt(String key) queryForInt(String key)}</li>
 *   <li>{@link #insert(String key) insert(String key)}</li>
 *   <li>{@link #delete(String key) delete(String key)}</li>
 * </ul>
 * <p/>
 * These methods should perform the named database operation.
 * <p/>
 * <b>Important:</b> Implementations of this should use <tt>String</tt> as the generic type as that is
 * what is required by Camel to allow using the idempotent repository with the Idempotent Consumer EIP
 * and also as file consumer read-lock. It was a mistake to make {@link IdempotentRepository} parameterized,
 * as it should have been a pre-configured to use a <tt>String</tt> type.
 */
@ManagedResource(description = "JDBC IdempotentRepository")
public abstract class AbstractJdbcMessageIdRepository extends ServiceSupport implements IdempotentRepository {

    protected JdbcTemplate jdbcTemplate;
    protected String processorName;
    protected TransactionTemplate transactionTemplate;
    protected DataSource dataSource;
    protected Logger log = LoggerFactory.getLogger(getClass());

    public AbstractJdbcMessageIdRepository() {
    }

    public AbstractJdbcMessageIdRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public AbstractJdbcMessageIdRepository(DataSource dataSource, TransactionTemplate transactionTemplate, String processorName) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.afterPropertiesSet();
        this.processorName = processorName;
        this.transactionTemplate = transactionTemplate;
    }

    public AbstractJdbcMessageIdRepository(DataSource dataSource, String processorName) {
        this(dataSource, createTransactionTemplate(dataSource), processorName);
    }

    /**
     * Operation that returns the number of rows, if any, for the specified key
     *
     * @param key  the key
     * @return int number of rows
     */
    protected abstract int queryForInt(String key);

    /**
     * Operation that inserts the key if it does not already exist
     *
     * @param key  the key
     * @return int number of rows inserted
     */
    protected abstract int insert(String key);

    /**
     * Operations that deletes the key if it exists
     *
     * @param key  the key
     * @return int number of rows deleted
     */
    protected abstract int delete(String key);
    
    /**
     * Operations that deletes all the rows
     *
     * @return int number of rows deleted
     */
    protected abstract int delete();

    /**
     * Creates the transaction template
     */
    protected static TransactionTemplate createTransactionTemplate(DataSource dataSource) {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate;
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
    }

    @ManagedOperation(description = "Adds the key to the store")
    @Override
    public boolean add(final String key) {
        // Run this in single transaction.
        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                int count = queryForInt(key);
                if (count == 0) {
                    insert(key);
                    return Boolean.TRUE;
                } else {
                    return Boolean.FALSE;
                }
            }
        });
        return rc.booleanValue();
    }

    @ManagedOperation(description = "Does the store contain the given key")
    @Override
    public boolean contains(final String key) {
        // Run this in single transaction.
        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                int count = queryForInt(key);
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
    @Override
    public boolean remove(final String key) {
        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                int updateCount = delete(key);
                if (updateCount == 0) {
                    return Boolean.FALSE;
                } else {
                    return Boolean.TRUE;
                }
            }
        });
        return rc.booleanValue();
    }
    
    @ManagedOperation(description = "Clear the store")
    @Override
    public void clear() {
        transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                delete();
                return Boolean.TRUE;
            }
        });
    }

    @Override
    public boolean confirm(final String key) {
        return true;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getProcessorName() {
        return processorName;
    }

    public void setProcessorName(String processorName) {
        this.processorName = processorName;
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

}
