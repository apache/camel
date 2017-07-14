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
package org.apache.camel.component.hawtdb;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtdb.api.OptimisticUpdateException;
import org.fusesource.hawtdb.api.SortedIndex;
import org.fusesource.hawtdb.api.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An instance of AggregationRepository which is backed by a HawtDB.
 */
public class HawtDBAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository, OptimisticLockingAggregationRepository {

    private static final Logger LOG = LoggerFactory.getLogger(HawtDBAggregationRepository.class);
    private HawtDBFile hawtDBFile;
    private String persistentFileName;
    private String repositoryName;
    private int bufferSize = 8 * 1024 * 1024;
    private boolean sync = true;
    private short pageSize = 512;
    private boolean returnOldExchange;
    private HawtDBCamelCodec codec = new HawtDBCamelCodec();
    private long recoveryInterval = 5000;
    private boolean useRecovery = true;
    private int maximumRedeliveries;
    private String deadLetterUri;
    
    private boolean allowSerializedHeaders;

    /**
     * Creates an aggregation repository
     */
    public HawtDBAggregationRepository() {
    }

    /**
     * Creates an aggregation repository
     *
     * @param repositoryName the repository name
     */
    public HawtDBAggregationRepository(String repositoryName) {
        ObjectHelper.notEmpty(repositoryName, "repositoryName");
        this.repositoryName = repositoryName;
    }

    /**
     * Creates an aggregation repository using a new {@link org.apache.camel.component.hawtdb.HawtDBFile}
     * that persists using the provided file.
     *
     * @param repositoryName     the repository name
     * @param persistentFileName the persistent store filename
     */
    public HawtDBAggregationRepository(String repositoryName, String persistentFileName) {
        ObjectHelper.notEmpty(repositoryName, "repositoryName");
        ObjectHelper.notEmpty(persistentFileName, "persistentFileName");
        this.repositoryName = repositoryName;
        this.persistentFileName = persistentFileName;
    }

    /**
     * Creates an aggregation repository using the provided {@link org.apache.camel.component.hawtdb.HawtDBFile}.
     *
     * @param repositoryName the repository name
     * @param hawtDBFile     the hawtdb file to use as persistent store
     */
    public HawtDBAggregationRepository(String repositoryName, HawtDBFile hawtDBFile) {
        ObjectHelper.notEmpty(repositoryName, "repositoryName");
        ObjectHelper.notNull(hawtDBFile, "hawtDBFile");
        this.hawtDBFile = hawtDBFile;
        this.repositoryName = repositoryName;
    }

    public Exchange add(final CamelContext camelContext, final String key, final Exchange exchange) {
        return doAdd(camelContext, key, exchange, true);
    }

    public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) throws OptimisticLockingException {
        try {
            return doAdd(camelContext, key, newExchange, false);
        } catch (OptimisticUpdateException e) {
            throw new OptimisticLockingException();
        }
    }

    protected Exchange doAdd(final CamelContext camelContext, final String key, final Exchange exchange, final boolean handleOptimisticLockingException) {
        LOG.debug("Adding key [{}] -> {}", key, exchange);
        try {
            // If we could guarantee that the key and exchange are immutable,
            // then we could have stuck them directly into the index, 
            // HawtDB could then eliminate the need to marshal and un-marshal  
            // in some cases.  But since we can't.. we are going to force
            // early marshaling.
            final Buffer keyBuffer = codec.marshallKey(key);
            final Buffer exchangeBuffer = codec.marshallExchange(camelContext, exchange, allowSerializedHeaders);
            Buffer rc = hawtDBFile.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    SortedIndex<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, repositoryName, true);
                    Buffer buffer = index.put(keyBuffer, exchangeBuffer);
                    LOG.trace("Added key index {}", keyBuffer);
                    return buffer;
                }

                @Override
                public String toString() {
                    return "Adding key [" + key + "]";
                }
            }, handleOptimisticLockingException);
            if (rc == null) {
                return null;
            }

            // only return old exchange if enabled
            if (isReturnOldExchange()) {
                return codec.unmarshallExchange(camelContext, rc);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error adding to repository " + repositoryName + " with key " + key, e);
        }

        return null;
    }

    public Exchange get(final CamelContext camelContext, final String key) {
        Exchange answer = null;
        try {
            final Buffer keyBuffer = codec.marshallKey(key);
            Buffer rc = hawtDBFile.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    SortedIndex<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, repositoryName, false);
                    if (index == null) {
                        return null;
                    }
                    Buffer buffer = index.get(keyBuffer);
                    LOG.trace("Getting key index {}", keyBuffer);
                    return buffer;
                }

                @Override
                public String toString() {
                    return "Getting key [" + key + "]";
                }
            });
            if (rc != null) {
                answer = codec.unmarshallExchange(camelContext, rc);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error getting key " + key + " from repository " + repositoryName, e);
        }

        LOG.debug("Getting key  [{}] -> {}", key, answer);
        return answer;
    }

    public void remove(final CamelContext camelContext, final String key, final Exchange exchange) {
        LOG.debug("Removing key [{}]", key);
        try {
            final Buffer keyBuffer = codec.marshallKey(key);
            final Buffer confirmKeyBuffer = codec.marshallKey(exchange.getExchangeId());
            final Buffer exchangeBuffer = codec.marshallExchange(camelContext, exchange, allowSerializedHeaders);
            hawtDBFile.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    SortedIndex<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, repositoryName, true);
                    // remove from the in progress index
                    Buffer buffer = index.remove(keyBuffer);
                    LOG.trace("Removed key index {} -> {}", keyBuffer, buffer);

                    // and add it to the confirmed index
                    SortedIndex<Buffer, Buffer> indexCompleted = hawtDBFile.getRepositoryIndex(tx, getRepositoryNameCompleted(), true);
                    indexCompleted.put(confirmKeyBuffer, exchangeBuffer);
                    LOG.trace("Added confirm index {}", confirmKeyBuffer);
                    return null;
                }

                @Override
                public String toString() {
                    return "Removing key [" + key + "]";
                }
            });

        } catch (IOException e) {
            throw new RuntimeException("Error removing key " + key + " from repository " + repositoryName, e);
        }
    }

    public void confirm(final CamelContext camelContext, final String exchangeId) {
        LOG.debug("Confirming exchangeId [{}]", exchangeId);
        try {
            final Buffer confirmKeyBuffer = codec.marshallKey(exchangeId);
            hawtDBFile.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    SortedIndex<Buffer, Buffer> indexCompleted = hawtDBFile.getRepositoryIndex(tx, getRepositoryNameCompleted(), true);
                    Buffer buffer = indexCompleted.remove(confirmKeyBuffer);
                    LOG.trace("Removed confirm index {} -> {}", confirmKeyBuffer, buffer);
                    return buffer;
                }

                @Override
                public String toString() {
                    return "Confirming exchangeId [" + exchangeId + "]";
                }
            });

        } catch (IOException e) {
            throw new RuntimeException("Error confirming exchangeId " + exchangeId + " from repository " + repositoryName, e);
        }
    }

    public Set<String> getKeys() {
        final Set<String> keys = new LinkedHashSet<String>();

        hawtDBFile.execute(new Work<Buffer>() {
            public Buffer execute(Transaction tx) {
                // interval task could potentially be running while we are shutting down so check for that
                if (!isRunAllowed()) {
                    return null;
                }

                SortedIndex<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, repositoryName, false);
                if (index == null) {
                    return null;
                }

                Iterator<Map.Entry<Buffer, Buffer>> it = index.iterator();
                // scan could potentially be running while we are shutting down so check for that
                while (it.hasNext() && isRunAllowed()) {
                    Map.Entry<Buffer, Buffer> entry = it.next();
                    Buffer keyBuffer = entry.getKey();

                    String key;
                    try {
                        key  = codec.unmarshallKey(keyBuffer);
                    } catch (IOException e) {
                        throw new RuntimeException("Error unmarshalling key: " + keyBuffer, e);
                    }
                    if (key != null) {
                        LOG.trace("getKey [{}]", key);
                        keys.add(key);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "getKeys";
            }
        });

        return Collections.unmodifiableSet(keys);
    }

    public Set<String> scan(CamelContext camelContext) {
        final Set<String> answer = new LinkedHashSet<String>();
        hawtDBFile.execute(new Work<Buffer>() {
            public Buffer execute(Transaction tx) {
                // scan could potentially be running while we are shutting down so check for that
                if (!isRunAllowed()) {
                    return null;
                }

                SortedIndex<Buffer, Buffer> indexCompleted = hawtDBFile.getRepositoryIndex(tx, getRepositoryNameCompleted(), false);
                if (indexCompleted == null) {
                    return null;
                }

                Iterator<Map.Entry<Buffer, Buffer>> it = indexCompleted.iterator();
                // scan could potentially be running while we are shutting down so check for that
                while (it.hasNext() && isRunAllowed()) {
                    Map.Entry<Buffer, Buffer> entry = it.next();
                    Buffer keyBuffer = entry.getKey();

                    String exchangeId;
                    try {
                        exchangeId = codec.unmarshallKey(keyBuffer);
                    } catch (IOException e) {
                        throw new RuntimeException("Error unmarshalling confirm key: " + keyBuffer, e);
                    }
                    if (exchangeId != null) {
                        LOG.trace("Scan exchangeId [{}]", exchangeId);
                        answer.add(exchangeId);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "Scan";
            }
        });

        if (answer.size() == 0) {
            LOG.trace("Scanned and found no exchange to recover.");
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scanned and found {} exchange(s) to recover (note some of them may already be in progress).", answer.size());
            }
        }
        return answer;

    }

    public Exchange recover(CamelContext camelContext, final String exchangeId) {
        Exchange answer = null;
        try {
            final Buffer confirmKeyBuffer = codec.marshallKey(exchangeId);
            Buffer rc = hawtDBFile.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    SortedIndex<Buffer, Buffer> indexCompleted = hawtDBFile.getRepositoryIndex(tx, getRepositoryNameCompleted(), false);
                    if (indexCompleted == null) {
                        return null;
                    }
                    return indexCompleted.get(confirmKeyBuffer);
                }

                @Override
                public String toString() {
                    return "Recovering exchangeId [" + exchangeId + "]";
                }
            });

            if (rc != null) {
                answer = codec.unmarshallExchange(camelContext, rc);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error recovering exchangeId " + exchangeId + " from repository " + repositoryName, e);
        }

        LOG.debug("Recovering exchangeId [{}] -> {}", exchangeId, answer);
        return answer;
    }

    private int size(final String repositoryName) {
        int answer = hawtDBFile.execute(new Work<Integer>() {
            public Integer execute(Transaction tx) {
                SortedIndex<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, repositoryName, false);
                return index != null ? index.size() : 0;
            }

            @Override
            public String toString() {
                return "Size[" + repositoryName + "]";
            }
        });

        LOG.debug("Size of repository [{}] -> {}", repositoryName, answer);
        return answer;
    }

    public HawtDBFile getHawtDBFile() {
        return hawtDBFile;
    }

    public void setHawtDBFile(HawtDBFile hawtDBFile) {
        this.hawtDBFile = hawtDBFile;
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

    public String getPersistentFileName() {
        return persistentFileName;
    }

    public void setPersistentFileName(String persistentFileName) {
        this.persistentFileName = persistentFileName;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public Integer getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(Integer bufferSize) {
        this.bufferSize = bufferSize;
    }

    public boolean isReturnOldExchange() {
        return returnOldExchange;
    }

    public void setReturnOldExchange(boolean returnOldExchange) {
        this.returnOldExchange = returnOldExchange;
    }

    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
    }

    public long getRecoveryIntervalInMillis() {
        return recoveryInterval;
    }

    public boolean isUseRecovery() {
        return useRecovery;
    }

    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public short getPageSize() {
        return pageSize;
    }

    public void setPageSize(short pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    @Override
    protected void doStart() throws Exception {
        // either we have a HawtDB configured or we use a provided fileName
        if (hawtDBFile == null && persistentFileName != null) {
            hawtDBFile = new HawtDBFile();
            hawtDBFile.setFile(new File(persistentFileName));
            hawtDBFile.setSync(isSync());
            if (getBufferSize() != null) {
                hawtDBFile.setMappingSegementSize(getBufferSize());
            }
            if (getPageSize() > 0) {
                hawtDBFile.setPageSize(getPageSize());
            }
        }

        ObjectHelper.notNull(hawtDBFile, "Either set a persistentFileName or a hawtDBFile");
        ObjectHelper.notNull(repositoryName, "repositoryName");

        ServiceHelper.startService(hawtDBFile);

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
        ServiceHelper.stopService(hawtDBFile);
    }

}
