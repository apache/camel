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
package org.apache.camel.component.leveldb;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.fusesource.hawtbuf.Buffer;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An instance of {@link org.apache.camel.spi.AggregationRepository} which is backed by a {@link LevelDBFile}.
 */
public class LevelDBAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository {

    private static final Logger LOG = LoggerFactory.getLogger(LevelDBAggregationRepository.class);

    private LevelDBFile levelDBFile;
    private String persistentFileName;
    private String repositoryName;
    private boolean sync;
    private boolean returnOldExchange;
    private LevelDBCamelCodec codec = new LevelDBCamelCodec();
    private long recoveryInterval = 5000;
    private boolean useRecovery = true;
    private int maximumRedeliveries;
    private String deadLetterUri;
    private boolean allowSerializedHeaders;

    /**
     * Creates an aggregation repository
     */
    public LevelDBAggregationRepository() {
    }

    /**
     * Creates an aggregation repository
     *
     * @param repositoryName the repository name
     */
    public LevelDBAggregationRepository(String repositoryName) {
        StringHelper.notEmpty(repositoryName, "repositoryName");
        this.repositoryName = repositoryName;
    }

    /**
     * Creates an aggregation repository using a new {@link LevelDBFile}
     * that persists using the provided file.
     *
     * @param repositoryName     the repository name
     * @param persistentFileName the persistent store filename
     */
    public LevelDBAggregationRepository(String repositoryName, String persistentFileName) {
        StringHelper.notEmpty(repositoryName, "repositoryName");
        StringHelper.notEmpty(persistentFileName, "persistentFileName");
        this.repositoryName = repositoryName;
        this.persistentFileName = persistentFileName;
    }

    /**
     * Creates an aggregation repository using the provided {@link LevelDBFile}.
     *
     * @param repositoryName the repository name
     * @param levelDBFile    the leveldb file to use as persistent store
     */
    public LevelDBAggregationRepository(String repositoryName, LevelDBFile levelDBFile) {
        StringHelper.notEmpty(repositoryName, "repositoryName");
        ObjectHelper.notNull(levelDBFile, "levelDBFile");
        this.levelDBFile = levelDBFile;
        this.repositoryName = repositoryName;
    }

    @Override
    public Exchange add(final CamelContext camelContext, final String key, final Exchange exchange) {
        LOG.debug("Adding key [{}] -> {}", key, exchange);
        try {
            byte[] lDbKey = keyBuilder(repositoryName, key);
            final Buffer exchangeBuffer = codec.marshallExchange(camelContext, exchange, allowSerializedHeaders);

            byte[] rc = null;
            if (isReturnOldExchange()) {
                rc = levelDBFile.getDb().get(lDbKey);
            }

            LOG.trace("Adding key index {} for repository {}", key, repositoryName);
            levelDBFile.getDb().put(lDbKey, exchangeBuffer.toByteArray(), levelDBFile.getWriteOptions());
            LOG.trace("Added key index {}", key);

            if (rc == null) {
                return null;
            }

            // only return old exchange if enabled
            if (isReturnOldExchange()) {
                return codec.unmarshallExchange(camelContext, new Buffer(rc));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error adding to repository " + repositoryName + " with key " + key, e);
        }

        return null;
    }

    @Override
    public Exchange get(final CamelContext camelContext, final String key) {
        Exchange answer = null;

        try {
            byte[] lDbKey = keyBuilder(repositoryName, key);
            LOG.trace("Getting key index {}", key);
            byte[] rc = levelDBFile.getDb().get(lDbKey);

            if (rc != null) {
                answer = codec.unmarshallExchange(camelContext, new Buffer(rc));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error getting key " + key + " from repository " + repositoryName, e);
        }

        LOG.debug("Getting key  [{}] -> {}", key, answer);
        return answer;
    }

    @Override
    public void remove(final CamelContext camelContext, final String key, final Exchange exchange) {
        LOG.debug("Removing key [{}]", key);

        try {
            byte[] lDbKey = keyBuilder(repositoryName, key);
            final String exchangeId = exchange.getExchangeId();
            final Buffer exchangeBuffer = codec.marshallExchange(camelContext, exchange, allowSerializedHeaders);

            // remove the exchange
            byte[] rc = levelDBFile.getDb().get(lDbKey);

            if (rc != null) {
                WriteBatch batch = levelDBFile.getDb().createWriteBatch();
                try {
                    batch.delete(lDbKey);
                    LOG.trace("Removed key index {} -> {}", key, new Buffer(rc));

                    // add exchange to confirmed index
                    byte[] confirmedLDBKey = keyBuilder(getRepositoryNameCompleted(), exchangeId);
                    batch.put(confirmedLDBKey, exchangeBuffer.toByteArray());
                    LOG.trace("Added confirm index {} for repository {}", exchangeId, getRepositoryNameCompleted());

                    levelDBFile.getDb().write(batch, levelDBFile.getWriteOptions());
                } finally {
                    batch.close();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Error removing key " + key + " from repository " + repositoryName, e);
        }
    }

    @Override
    public void confirm(final CamelContext camelContext, final String exchangeId) {
        LOG.debug("Confirming exchangeId [{}]", exchangeId);

        byte[] confirmedLDBKey = keyBuilder(getRepositoryNameCompleted(), exchangeId);

        byte[] rc = levelDBFile.getDb().get(confirmedLDBKey);

        if (rc != null) {
            levelDBFile.getDb().delete(confirmedLDBKey);
            LOG.trace("Removed confirm index {} -> {}", exchangeId, new Buffer(rc));
        }
    }

    @Override
    public Set<String> getKeys() {
        final Set<String> keys = new LinkedHashSet<>();

        // interval task could potentially be running while we are shutting down so check for that
        if (!isRunAllowed()) {
            return null;
        }

        DBIterator it = levelDBFile.getDb().iterator();

        String keyBuffer;
        try {
            String prefix = repositoryName + '\0';
            for (it.seek(keyBuilder(repositoryName, "")); it.hasNext(); it.next()) {
                if (!isRunAllowed()) {
                    break;
                }
                keyBuffer = asString(it.peekNext().getKey());

                if (!keyBuffer.startsWith(prefix)) {
                    break;
                }

                String key = keyBuffer.substring(prefix.length());

                LOG.trace("getKey [{}]", key);
                keys.add(key);
            }
        } finally {
            // Make sure you close the iterator to avoid resource leaks.
            IOHelper.close(it);
        }

        return Collections.unmodifiableSet(keys);
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        final Set<String> answer = new LinkedHashSet<>();

        if (!isRunAllowed()) {
            return null;
        }

        DBIterator it = levelDBFile.getDb().iterator();

        String keyBuffer;
        try {
            String prefix = getRepositoryNameCompleted() + '\0';

            for (it.seek(keyBuilder(getRepositoryNameCompleted(), "")); it.hasNext(); it.next()) {
                keyBuffer = asString(it.peekNext().getKey());

                if (!keyBuffer.startsWith(prefix)) {
                    break;
                }
                String exchangeId = keyBuffer.substring(prefix.length());

                LOG.trace("Scan exchangeId [{}]", exchangeId);
                answer.add(exchangeId);
            }
        } finally {
            // Make sure you close the iterator to avoid resource leaks.
            IOHelper.close(it);
        }

        if (answer.size() == 0) {
            LOG.trace("Scanned and found no exchange to recover.");
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scanned and found {} exchange(s) to recover (note some of them may already be in progress).", answer.size());
            }
        }
        return answer;

    }

    @Override
    public Exchange recover(CamelContext camelContext, final String exchangeId) {
        Exchange answer = null;

        try {
            byte[] completedLDBKey = keyBuilder(getRepositoryNameCompleted(), exchangeId);

            byte[] rc = levelDBFile.getDb().get(completedLDBKey);

            if (rc != null) {
                answer = codec.unmarshallExchange(camelContext, new Buffer(rc));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error recovering exchangeId " + exchangeId + " from repository " + repositoryName, e);
        }

        LOG.debug("Recovering exchangeId [{}] -> {}", exchangeId, answer);
        return answer;
    }

    private int size(final String repositoryName) {
        DBIterator it = levelDBFile.getDb().iterator();

        String prefix = repositoryName + '\0';
        int count = 0;
        try {
            for (it.seek(keyBuilder(repositoryName, "")); it.hasNext(); it.next()) {
                if (!asString(it.peekNext().getKey()).startsWith(prefix)) {
                    break;
                }
                count++;
            }
        } finally {
            // Make sure you close the iterator to avoid resource leaks.
            IOHelper.close(it);
        }

        LOG.debug("Size of repository [{}] -> {}", repositoryName, count);
        return count;
    }

    public LevelDBFile getLevelDBFile() {
        return levelDBFile;
    }

    public void setLevelDBFile(LevelDBFile levelDBFile) {
        this.levelDBFile = levelDBFile;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    private String getRepositoryNameCompleted() {
        return repositoryName + "-completed";
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean isReturnOldExchange() {
        return returnOldExchange;
    }

    public void setReturnOldExchange(boolean returnOldExchange) {
        this.returnOldExchange = returnOldExchange;
    }

    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    @Override
    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
    }

    @Override
    public long getRecoveryIntervalInMillis() {
        return recoveryInterval;
    }

    @Override
    public boolean isUseRecovery() {
        return useRecovery;
    }

    @Override
    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    @Override
    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    @Override
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    @Override
    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    @Override
    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public String getPersistentFileName() {
        return persistentFileName;
    }

    public void setPersistentFileName(String persistentFileName) {
        this.persistentFileName = persistentFileName;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    @Override
    protected void doStart() throws Exception {
        // either we have a LevelDB configured or we use a provided fileName
        if (levelDBFile == null && persistentFileName != null) {
            levelDBFile = new LevelDBFile();
            levelDBFile.setSync(isSync());
            levelDBFile.setFileName(persistentFileName);
        }

        ObjectHelper.notNull(levelDBFile, "Either set a persistentFileName or a levelDBFile");
        ObjectHelper.notNull(repositoryName, "repositoryName");

        ServiceHelper.startService(levelDBFile);

        // log number of existing exchanges
        int current = size(getRepositoryName());
        int completed = size(getRepositoryNameCompleted());

        if (current > 0) {
            LOG.info("On startup there are " + current + " aggregate exchanges (not completed) in repository: " + getRepositoryName());
        } else {
            LOG.info("On startup there are no existing aggregate exchanges (not completed) in repository: " + getRepositoryName());
        }
        if (completed > 0) {
            LOG.warn("On startup there are " + completed + " completed exchanges to be recovered in repository: " + getRepositoryNameCompleted());
        } else {
            LOG.info("On startup there are no completed exchanges to be recovered in repository: " + getRepositoryNameCompleted());
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(levelDBFile);
    }

    public static byte[] keyBuilder(String repo, String key) {
        try {
            return (repo + '\0' + key).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String asString(byte[] value) {
        if (value == null) {
            return null;
        } else {
            try {
                return new String(value, "UTF-8");
            } catch (UnsupportedEncodingException var2) {
                throw new RuntimeException(var2);
            }
        }
    }

}
