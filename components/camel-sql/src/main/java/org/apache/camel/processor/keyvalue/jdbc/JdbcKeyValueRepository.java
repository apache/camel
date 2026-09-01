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
package org.apache.camel.processor.keyvalue.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A JDBC-based implementation of {@link KeyValueRepository} that stores entries in a database table.
 * <p/>
 * Values are serialized using Java object serialization and stored as BLOB. Each entry may optionally have a
 * time-to-live (TTL); expired entries are lazily evicted on access and during {@link #keys()} scans.
 * <p/>
 * The table is created automatically on startup if it does not already exist (controlled by
 * {@link #setCreateTableIfNotExists(boolean)}).
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A JDBC-based KeyValueRepository that stores entries in a database table.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "JDBC based key-value repository")
public class JdbcKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    protected static final String DEFAULT_TABLENAME = "CAMEL_KEYVALUE";
    protected static final String DEFAULT_TABLE_EXISTS_STRING = "SELECT 1 FROM CAMEL_KEYVALUE WHERE 1 = 0";
    protected static final String DEFAULT_CREATE_STRING
            = "CREATE TABLE CAMEL_KEYVALUE (ITEM_KEY VARCHAR(512) NOT NULL, ITEM_VALUE BLOB NOT NULL, "
              + "EXPIRES_AT BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (ITEM_KEY))";
    protected static final String DEFAULT_SELECT_STRING
            = "SELECT ITEM_VALUE, EXPIRES_AT FROM CAMEL_KEYVALUE WHERE ITEM_KEY = ?";
    protected static final String DEFAULT_INSERT_STRING
            = "INSERT INTO CAMEL_KEYVALUE (ITEM_KEY, ITEM_VALUE, EXPIRES_AT) VALUES (?, ?, ?)";
    protected static final String DEFAULT_DELETE_STRING = "DELETE FROM CAMEL_KEYVALUE WHERE ITEM_KEY = ?";
    protected static final String DEFAULT_CLEAR_STRING = "DELETE FROM CAMEL_KEYVALUE";
    protected static final String DEFAULT_SELECT_KEYS_STRING
            = "SELECT ITEM_KEY FROM CAMEL_KEYVALUE WHERE EXPIRES_AT = 0 OR EXPIRES_AT > ?";
    protected static final String DEFAULT_DELETE_EXPIRED_STRING
            = "DELETE FROM CAMEL_KEYVALUE WHERE EXPIRES_AT > 0 AND EXPIRES_AT <= ?";

    private static final Logger LOG = LoggerFactory.getLogger(JdbcKeyValueRepository.class);

    @Metadata(description = "The Spring JdbcTemplate to use for connecting to the database", required = true)
    private JdbcTemplate jdbcTemplate;
    @Metadata(description = "The Spring TransactionTemplate to use for connecting to the database", required = true)
    private TransactionTemplate transactionTemplate;
    private DataSource dataSource;

    @Metadata(description = "The name of the table to use in the database", defaultValue = "CAMEL_KEYVALUE")
    private String tableName;
    @Metadata(description = "Whether to create the table in the database if none exists on startup", defaultValue = "true")
    private boolean createTableIfNotExists = true;

    @Metadata(label = "advanced", description = "SQL query to use for checking if table exists")
    private String tableExistsString = DEFAULT_TABLE_EXISTS_STRING;
    @Metadata(label = "advanced", description = "SQL query to use for creating table")
    private String createString = DEFAULT_CREATE_STRING;
    @Metadata(label = "advanced", description = "SQL query to use for selecting a value by key")
    private String selectString = DEFAULT_SELECT_STRING;
    @Metadata(label = "advanced", description = "SQL query to use for inserting a new entry")
    private String insertString = DEFAULT_INSERT_STRING;
    @Metadata(label = "advanced", description = "SQL query to use for deleting an entry by key")
    private String deleteString = DEFAULT_DELETE_STRING;
    @Metadata(label = "advanced", description = "SQL query to delete all entries from the table")
    private String clearString = DEFAULT_CLEAR_STRING;
    @Metadata(label = "advanced", description = "SQL query to use for selecting all non-expired keys")
    private String selectKeysString = DEFAULT_SELECT_KEYS_STRING;
    @Metadata(label = "advanced", description = "SQL query to use for deleting expired entries")
    private String deleteExpiredString = DEFAULT_DELETE_EXPIRED_STRING;

    /**
     * Creates a new JDBC key-value repository. A {@link DataSource} or {@link JdbcTemplate} must be set before
     * initialization.
     */
    public JdbcKeyValueRepository() {
    }

    /**
     * Creates a new JDBC key-value repository using the given data source. A {@link JdbcTemplate} and
     * {@link TransactionTemplate} will be created automatically during initialization.
     *
     * @param dataSource the data source to use
     */
    public JdbcKeyValueRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates a new JDBC key-value repository using the given JDBC template and transaction template.
     *
     * @param jdbcTemplate        the JDBC template for database access
     * @param transactionTemplate the transaction template for transactional operations
     */
    public JdbcKeyValueRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Creates a {@link TransactionTemplate} from the given data source with {@code PROPAGATION_REQUIRED}.
     *
     * @param  dataSource the data source to create the transaction template from
     * @return            a configured transaction template
     */
    protected static TransactionTemplate createTransactionTemplate(DataSource dataSource) {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (dataSource != null && jdbcTemplate == null) {
            jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.afterPropertiesSet();
        }
        if (dataSource != null && transactionTemplate == null) {
            transactionTemplate = createTransactionTemplate(dataSource);
        }

        if (tableName != null) {
            // update query strings from default table name to the custom table name
            tableExistsString = DEFAULT_TABLE_EXISTS_STRING.replace(DEFAULT_TABLENAME, tableName);
            createString = DEFAULT_CREATE_STRING.replace(DEFAULT_TABLENAME, tableName);
            selectString = DEFAULT_SELECT_STRING.replace(DEFAULT_TABLENAME, tableName);
            insertString = DEFAULT_INSERT_STRING.replace(DEFAULT_TABLENAME, tableName);
            deleteString = DEFAULT_DELETE_STRING.replace(DEFAULT_TABLENAME, tableName);
            clearString = DEFAULT_CLEAR_STRING.replace(DEFAULT_TABLENAME, tableName);
            selectKeysString = DEFAULT_SELECT_KEYS_STRING.replace(DEFAULT_TABLENAME, tableName);
            deleteExpiredString = DEFAULT_DELETE_EXPIRED_STRING.replace(DEFAULT_TABLENAME, tableName);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        boolean tableExists = transactionTemplate.execute(status -> {
            try {
                // we will receive an exception if the table doesn't exist or we cannot access it
                jdbcTemplate.execute(getTableExistsString());
                LOG.debug("Expected table for JdbcKeyValueRepository exists");
                return true;
            } catch (DataAccessException e) {
                LOG.debug("Expected table for JdbcKeyValueRepository does not exist");
                return false;
            }
        });

        if (!tableExists && createTableIfNotExists) {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    LOG.debug("Creating table for JdbcKeyValueRepository because it doesn't exist...");
                    jdbcTemplate.execute(getCreateString());
                    LOG.info("Table created with query '{}'", getCreateString());
                } catch (DataAccessException dae) {
                    LOG.error(
                            "Can't create table for JdbcKeyValueRepository with query '{}' because of: {}. "
                              + "This may be a permissions problem. Please create this table and try again.",
                            getCreateString(), dae.getMessage());
                    throw dae;
                }
            });
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    @ManagedOperation(description = "Get value by key")
    public Object get(String key) {
        return transactionTemplate.execute(status -> doGet(key));
    }

    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public Object put(String key, Object value, long ttlMillis) {
        return transactionTemplate.execute(status -> {
            Object oldValue = doGet(key);
            // delete any existing row (whether expired or not)
            jdbcTemplate.update(getDeleteString(), key);
            // insert the new row
            long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : 0;
            jdbcTemplate.update(getInsertString(), key, serialize(value), expiresAt);
            return oldValue;
        });
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public Object delete(String key) {
        return transactionTemplate.execute(status -> {
            Object oldValue = doGet(key);
            jdbcTemplate.update(getDeleteString(), key);
            return oldValue;
        });
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        Boolean result = transactionTemplate.execute(status -> doGet(key) != null);
        return result != null && result;
    }

    @Override
    public Set<String> keys() {
        return transactionTemplate.execute(status -> {
            // first delete expired entries
            long now = System.currentTimeMillis();
            jdbcTemplate.update(getDeleteExpiredString(), now);
            // then select all non-expired keys
            List<String> keyList = jdbcTemplate.queryForList(getSelectKeysString(), String.class, now);
            return Collections.unmodifiableSet(new LinkedHashSet<>(keyList));
        });
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(getClearString()));
    }

    @Override
    public Object putIfAbsent(String key, Object value, long ttlMillis) {
        return transactionTemplate.execute(status -> {
            // check if a non-expired entry already exists
            Object existing = doGet(key);
            if (existing != null) {
                return existing;
            }
            // attempt to insert
            long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : 0;
            try {
                jdbcTemplate.update(getInsertString(), key, serialize(value), expiresAt);
                return null;
            } catch (DuplicateKeyException e) {
                // concurrent insert race -- another thread/node won
                LOG.debug("Concurrent insert race for key '{}' -- another thread won, treating as existing", key);
                status.setRollbackOnly();
                // re-read the value that the other thread inserted
                Object concurrentValue = doGet(key);
                return concurrentValue;
            }
        });
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        return keys().size();
    }

    /**
     * Internal get that reads the value for the given key within the current transaction. If the entry has expired, it
     * is deleted and {@code null} is returned.
     *
     * @param  key the key to look up
     * @return     the deserialized value, or {@code null} if not found or expired
     */
    private Object doGet(String key) {
        try {
            return jdbcTemplate.queryForObject(getSelectString(), (rs, rowNum) -> {
                byte[] bytes = rs.getBytes(1);
                long expiresAt = rs.getLong(2);
                if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
                    // entry has expired -- delete it
                    jdbcTemplate.update(getDeleteString(), key);
                    return null;
                }
                return deserialize(bytes);
            }, key);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Serializes an object to a byte array using Java object serialization.
     *
     * @param  value                 the object to serialize
     * @return                       the serialized bytes
     * @throws RuntimeCamelException if serialization fails
     */
    private byte[] serialize(Object value) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to serialize value", e);
        }
    }

    /**
     * Deserializes a byte array back into an object using Java object serialization.
     *
     * @param  bytes                 the bytes to deserialize
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails
     */
    private Object deserialize(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeCamelException("Failed to deserialize value", e);
        }
    }

    // ---- Getters and Setters ----

    @ManagedAttribute(description = "The name of the database table")
    public String getTableName() {
        return tableName;
    }

    /**
     * To use a custom table name instead of the default name: CAMEL_KEYVALUE
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @ManagedAttribute(description = "Whether to create the table if it does not exist on startup")
    public boolean isCreateTableIfNotExists() {
        return createTableIfNotExists;
    }

    public void setCreateTableIfNotExists(boolean createTableIfNotExists) {
        this.createTableIfNotExists = createTableIfNotExists;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    public String getTableExistsString() {
        return tableExistsString;
    }

    public void setTableExistsString(String tableExistsString) {
        this.tableExistsString = tableExistsString;
    }

    public String getCreateString() {
        return createString;
    }

    public void setCreateString(String createString) {
        this.createString = createString;
    }

    public String getSelectString() {
        return selectString;
    }

    public void setSelectString(String selectString) {
        this.selectString = selectString;
    }

    public String getInsertString() {
        return insertString;
    }

    public void setInsertString(String insertString) {
        this.insertString = insertString;
    }

    public String getDeleteString() {
        return deleteString;
    }

    public void setDeleteString(String deleteString) {
        this.deleteString = deleteString;
    }

    public String getClearString() {
        return clearString;
    }

    public void setClearString(String clearString) {
        this.clearString = clearString;
    }

    public String getSelectKeysString() {
        return selectKeysString;
    }

    public void setSelectKeysString(String selectKeysString) {
        this.selectKeysString = selectKeysString;
    }

    public String getDeleteExpiredString() {
        return deleteExpiredString;
    }

    public void setDeleteExpiredString(String deleteExpiredString) {
        this.deleteExpiredString = deleteExpiredString;
    }
}
