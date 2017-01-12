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
package org.apache.camel.component.couchbase;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.couchbase.client.CouchbaseClient;
import com.couchbase.client.CouchbaseConnectionFactoryBuilder;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_PUT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_URI_ERROR;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_CONSUME_PROCESSED_STRATEGY;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_COUCHBASE_PORT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_DESIGN_DOCUMENT_NAME;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_MAX_RECONNECT_DELAY;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_OBS_POLL_INTERVAL;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_OBS_TIMEOUT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_OP_QUEUE_MAX_BLOCK_TIME;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_OP_TIMEOUT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_PAUSE_BETWEEN_RETRIES;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_PRODUCER_RETRIES;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_READ_BUFFER_SIZE;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_TIMEOUT_EXCEPTION_THRESHOLD;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_VIEWNAME;

/**
 * Represents a Couchbase endpoint that can query Views with a Poll strategy
 * and/or produce various type of operations.
 */
@UriEndpoint(scheme = "couchbase", title = "Couchbase", syntax = "couchbase:url", consumerClass = CouchbaseConsumer.class, label = "database,nosql")
public class CouchbaseEndpoint extends ScheduledPollEndpoint {

    @UriPath
    private String protocol;
    private String bucket;
    private String hostname;
    private int port;

    // Couchbase key
    @UriParam
    private String key;

    // Authentication
    @UriParam
    private String username = "";
    @UriParam
    private String password = "";

    // Additional hosts
    @UriParam
    private String additionalHosts = "";

    // Persistence and replication parameters
    @UriParam
    private int persistTo;

    @UriParam
    private int replicateTo;

    // Producer parameters
    @UriParam
    private String operation = COUCHBASE_PUT;
    @UriParam
    private boolean autoStartIdForInserts;
    @UriParam
    private int producerRetryAttempts = DEFAULT_PRODUCER_RETRIES;
    @UriParam
    private int producerRetryPause = DEFAULT_PAUSE_BETWEEN_RETRIES;

    @UriParam
    private long startingIdForInsertsFrom;
    // View control
    @UriParam
    private String designDocumentName = DEFAULT_DESIGN_DOCUMENT_NAME;
    @UriParam
    private String viewName = DEFAULT_VIEWNAME;
    @UriParam
    private int limit = -1;
    @UriParam
    private boolean descending;
    @UriParam
    private int skip = -1;
    @UriParam
    private String rangeStartKey = "";

    @UriParam
    private String rangeEndKey = "";

    // Consumer strategy
    @UriParam
    private String consumerProcessedStrategy = DEFAULT_CONSUME_PROCESSED_STRATEGY;

    // Connection fine tuning parameters
    @UriParam
    private long opTimeOut = DEFAULT_OP_TIMEOUT;
    @UriParam
    private int timeoutExceptionThreshold = DEFAULT_TIMEOUT_EXCEPTION_THRESHOLD;
    @UriParam
    private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;
    @UriParam
    private boolean shouldOptimize;
    @UriParam
    private long maxReconnectDelay = DEFAULT_MAX_RECONNECT_DELAY;
    @UriParam
    private long opQueueMaxBlockTime = DEFAULT_OP_QUEUE_MAX_BLOCK_TIME;
    @UriParam
    private long obsPollInterval = DEFAULT_OBS_POLL_INTERVAL;
    @UriParam
    private long obsTimeout = DEFAULT_OBS_TIMEOUT;

    public CouchbaseEndpoint() {
    }

    public CouchbaseEndpoint(String uri, String remaining, CouchbaseComponent component) throws URISyntaxException {
        super(uri, component);
        URI remainingUri = new URI(remaining);

        protocol = remainingUri.getScheme();
        if (protocol == null) {
            throw new IllegalArgumentException(COUCHBASE_URI_ERROR);
        }

        port = remainingUri.getPort() == -1 ? DEFAULT_COUCHBASE_PORT : remainingUri.getPort();

        if (remainingUri.getPath() == null || remainingUri.getPath().trim().length() == 0) {
            throw new IllegalArgumentException(COUCHBASE_URI_ERROR);
        }
        bucket = remainingUri.getPath().substring(1);

        hostname = remainingUri.getHost();
        if (hostname == null) {
            throw new IllegalArgumentException(COUCHBASE_URI_ERROR);
        }
    }

    public CouchbaseEndpoint(String endpointUri, CouchbaseComponent component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new CouchbaseProducer(this, createClient(), persistTo, replicateTo);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new CouchbaseConsumer(this, createClient(), processor);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol to use
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getBucket() {
        return bucket;
    }

    /**
     * The bucket to use
     */
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname to use
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * The port number to use
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getKey() {
        return key;
    }

    /**
     * The key to use
     */
    public void setKey(String key) {
        this.key = key;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The username to use
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password to use
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getAdditionalHosts() {
        return additionalHosts;
    }

    /**
     * The additional hosts
     */
    public void setAdditionalHosts(String additionalHosts) {
        this.additionalHosts = additionalHosts;
    }

    public int getPersistTo() {
        return persistTo;
    }

    /**
     * Where to persist the data
     */
    public void setPersistTo(int persistTo) {
        this.persistTo = persistTo;
    }

    public int getReplicateTo() {
        return replicateTo;
    }

    /**
     * Where to replicate the data
     */
    public void setReplicateTo(int replicateTo) {
        this.replicateTo = replicateTo;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * The operation to do
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public boolean isAutoStartIdForInserts() {
        return autoStartIdForInserts;
    }

    /**
     * Define if we want an autostart Id when we are doing an insert operation
     */
    public void setAutoStartIdForInserts(boolean autoStartIdForInserts) {
        this.autoStartIdForInserts = autoStartIdForInserts;
    }

    public long getStartingIdForInsertsFrom() {
        return startingIdForInsertsFrom;
    }

    /**
     * Define the starting Id where we are doing an insert operation
     */
    public void setStartingIdForInsertsFrom(long startingIdForInsertsFrom) {
        this.startingIdForInsertsFrom = startingIdForInsertsFrom;
    }

    public int getProducerRetryAttempts() {
        return producerRetryAttempts;
    }

    /**
     * Define the number of retry attempts
     */
    public void setProducerRetryAttempts(int producerRetryAttempts) {
        this.producerRetryAttempts = producerRetryAttempts;
    }

    public int getProducerRetryPause() {
        return producerRetryPause;
    }

    /**
     * Define the retry pause between different attempts
     */
    public void setProducerRetryPause(int producerRetryPause) {
        this.producerRetryPause = producerRetryPause;
    }

    public String getDesignDocumentName() {
        return designDocumentName;
    }

    /**
     * The design document name to use
     */
    public void setDesignDocumentName(String designDocumentName) {
        this.designDocumentName = designDocumentName;
    }

    public String getViewName() {
        return viewName;
    }

    /**
     * The view name to use
     */
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public int getLimit() {
        return limit;
    }

    /**
     * The output limit to use
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean isDescending() {
        return descending;
    }

    /**
     * Define if this operation is descending or not
     */
    public void setDescending(boolean descending) {
        this.descending = descending;
    }

    public int getSkip() {
        return skip;
    }

    /**
     * Define the skip to use
     */
    public void setSkip(int skip) {
        this.skip = skip;
    }

    public String getRangeStartKey() {
        return rangeStartKey;
    }

    /**
     * Define a range for the start key
     */
    public void setRangeStartKey(String rangeStartKey) {
        this.rangeStartKey = rangeStartKey;
    }

    public String getRangeEndKey() {
        return rangeEndKey;
    }

    /**
     * Define a range for the end key
     */
    public void setRangeEndKey(String rangeEndKey) {
        this.rangeEndKey = rangeEndKey;
    }

    public String getConsumerProcessedStrategy() {
        return consumerProcessedStrategy;
    }

    /**
     * Define the consumer Processed strategy to use
     */
    public void setConsumerProcessedStrategy(String consumerProcessedStrategy) {
        this.consumerProcessedStrategy = consumerProcessedStrategy;
    }

    public long getOpTimeOut() {
        return opTimeOut;
    }

    /**
     * Define the operation timeout
     */
    public void setOpTimeOut(long opTimeOut) {
        this.opTimeOut = opTimeOut;
    }

    public int getTimeoutExceptionThreshold() {
        return timeoutExceptionThreshold;
    }

    /**
     * Define the threshold for throwing a timeout Exception
     */
    public void setTimeoutExceptionThreshold(int timeoutExceptionThreshold) {
        this.timeoutExceptionThreshold = timeoutExceptionThreshold;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    /**
     * Define the buffer size
     */
    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public boolean isShouldOptimize() {
        return shouldOptimize;
    }

    /**
     * Define if we want to use optimization or not where possible
     */
    public void setShouldOptimize(boolean shouldOptimize) {
        this.shouldOptimize = shouldOptimize;
    }

    public long getMaxReconnectDelay() {
        return maxReconnectDelay;
    }

    /**
     * Define the max delay during a reconnection
     */
    public void setMaxReconnectDelay(long maxReconnectDelay) {
        this.maxReconnectDelay = maxReconnectDelay;
    }

    public long getOpQueueMaxBlockTime() {
        return opQueueMaxBlockTime;
    }

    /**
     * Define the max time an operation can be in queue blocked
     */
    public void setOpQueueMaxBlockTime(long opQueueMaxBlockTime) {
        this.opQueueMaxBlockTime = opQueueMaxBlockTime;
    }

    public long getObsPollInterval() {
        return obsPollInterval;
    }

    /**
     * Define the observation polling interval
     */
    public void setObsPollInterval(long obsPollInterval) {
        this.obsPollInterval = obsPollInterval;
    }

    public long getObsTimeout() {
        return obsTimeout;
    }

    /**
     * Define the observation timeout
     */
    public void setObsTimeout(long obsTimeout) {
        this.obsTimeout = obsTimeout;
    }

    public URI[] makeBootstrapURI() throws URISyntaxException {

        if (additionalHosts == null || "".equals(additionalHosts)) {
            return new URI[] {new URI(protocol + "://" + hostname + ":" + port + "/pools")};
        }
        return getAllUris();

    }

    private URI[] getAllUris() throws URISyntaxException {

        String[] hosts = additionalHosts.split(",");

        for (int i = 0; i < hosts.length; i++) {
            hosts[i] = hosts[i].trim();
        }

        List<String> hostList = new ArrayList<String>();
        hostList.add(hostname);
        hostList.addAll(Arrays.asList(hosts));
        Set<String> hostSet = new LinkedHashSet<String>(hostList);
        hosts = hostSet.toArray(new String[hostSet.size()]);

        URI[] uriArray = new URI[hosts.length];

        for (int i = 0; i < hosts.length; i++) {
            uriArray[i] = new URI(protocol + "://" + hosts[i] + ":" + port + "/pools");
        }

        return uriArray;
    }

    private CouchbaseClient createClient() throws IOException, URISyntaxException {
        List<URI> hosts = Arrays.asList(makeBootstrapURI());

        CouchbaseConnectionFactoryBuilder cfb = new CouchbaseConnectionFactoryBuilder();

        if (opTimeOut != DEFAULT_OP_TIMEOUT) {
            cfb.setOpTimeout(opTimeOut);
        }
        if (timeoutExceptionThreshold != DEFAULT_TIMEOUT_EXCEPTION_THRESHOLD) {
            cfb.setTimeoutExceptionThreshold(timeoutExceptionThreshold);
        }
        if (readBufferSize != DEFAULT_READ_BUFFER_SIZE) {
            cfb.setReadBufferSize(readBufferSize);
        }
        if (shouldOptimize) {
            cfb.setShouldOptimize(true);
        }
        if (maxReconnectDelay != DEFAULT_MAX_RECONNECT_DELAY) {
            cfb.setMaxReconnectDelay(maxReconnectDelay);
        }
        if (opQueueMaxBlockTime != DEFAULT_OP_QUEUE_MAX_BLOCK_TIME) {
            cfb.setOpQueueMaxBlockTime(opQueueMaxBlockTime);
        }
        if (obsPollInterval != DEFAULT_OBS_POLL_INTERVAL) {
            cfb.setObsPollInterval(obsPollInterval);
        }
        if (obsTimeout != DEFAULT_OBS_TIMEOUT) {
            cfb.setObsTimeout(obsTimeout);
        }

        return new CouchbaseClient(cfb.buildCouchbaseConnection(hosts, bucket, username, password));

    }
}
