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

import com.couchbase.client.CouchbaseClient;
import com.couchbase.client.CouchbaseConnectionFactoryBuilder;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import static org.apache.camel.component.couchbase.CouchbaseConstants.*;

public class CouchbaseEndpoint extends DefaultEndpoint {

    private String protocol;
    private String bucket;
    private String hostname;
    private int port;

    private String key;

    private String operation = COUCHBASE_PUT;

    private boolean autoStartIdForInserts = false;

    private long startingIdForInsertsFrom = 0;
    private String designDocumentName = DEFAULT_DESIGN_DOCUMENT_NAME;

    private String viewName = DEFAULT_VIEWNAME;
    private int limit = -1;

    private boolean descending = false;
    private int skip = -1;

    private String rangeStartKey = "";

    private String rangeEndKey = "";
    private String username = "";

    private String password = "";

    // Parameters for Couchbase connection fine tuning
    private long opTimeOut = DEFAULT_OP_TIMEOUT;
    private int timeoutExceptionThreshold = DEFAULT_TIMEOUT_EXCEPTION_THRESHOLD;
    private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;
    private boolean shouldOptimize;
    private long maxReconnectDelay = DEFAULT_MAX_RECONNECT_DELAY;
    private long opQueueMaxBlockTime = DEFAULT_OP_QUEUE_MAX_BLOCK_TIME;
    private long obsPollInterval = DEFAULT_OBS_POLL_INTERVAL;
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

        port = remainingUri.getPort() == -1 ? COUCHBASE_DEFAULT_PORT : remainingUri.getPort();

        if (remainingUri.getPath() == null || remainingUri.getPath().trim().length() == 0) {
            throw new IllegalArgumentException(COUCHBASE_URI_ERROR);
        }
        bucket = remainingUri.getPath().substring(1);

        hostname = remainingUri.getHost();
        if (hostname == null) {
            throw new IllegalArgumentException(COUCHBASE_URI_ERROR);
        }
    }


    public CouchbaseEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        return new CouchbaseProducer(this, createClient());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CouchbaseConsumer(this, createClient(), processor);
    }
    public boolean isSingleton() {
        return true;
    }
    public String getProtocol() {
        return protocol;
    }
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }


    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public boolean isAutoStartIdForInserts() {
        return autoStartIdForInserts;
    }

    public void setAutoStartIdForInserts(boolean autoStartIdForInserts) {
        this.autoStartIdForInserts = autoStartIdForInserts;
    }

    public long getStartingIdForInsertsFrom() {
        return startingIdForInsertsFrom;
    }

    public void setStartingIdForInsertsFrom(long startingIdForInsertsFrom) {
        this.startingIdForInsertsFrom = startingIdForInsertsFrom;
    }

    public String getDesignDocumentName() {
        return designDocumentName;
    }

    public void setDesignDocumentName(String designDocumentName) {
        this.designDocumentName = designDocumentName;
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isDescending() {
        return descending;
    }

    public void setDescending(boolean descending) {
        this.descending = descending;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public String getRangeStartKey() {
        return rangeStartKey;
    }

    public void setRangeStartKey(String rangeStartKey) {
        this.rangeStartKey = rangeStartKey;
    }

    public String getRangeEndKey() {
        return rangeEndKey;
    }

    public void setRangeEndKey(String rangeEndKey) {
        this.rangeEndKey = rangeEndKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getOpTimeOut() {
        return opTimeOut;
    }

    public void setOpTimeOut(long opTimeOut) {
        this.opTimeOut = opTimeOut;
    }

    public int getTimeoutExceptionThreshold() {
        return timeoutExceptionThreshold;
    }

    public void setTimeoutExceptionThreshold(int timeoutExceptionThreshold) {
        this.timeoutExceptionThreshold = timeoutExceptionThreshold;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public boolean isShouldOptimize() {
        return shouldOptimize;
    }

    public void setShouldOptimize(boolean shouldOptimize) {
        this.shouldOptimize = shouldOptimize;
    }

    public long getMaxReconnectDelay() {
        return maxReconnectDelay;
    }

    public void setMaxReconnectDelay(long maxReconnectDelay) {
        this.maxReconnectDelay = maxReconnectDelay;
    }

    public long getOpQueueMaxBlockTime() {
        return opQueueMaxBlockTime;
    }

    public void setOpQueueMaxBlockTime(long opQueueMaxBlockTime) {
        this.opQueueMaxBlockTime = opQueueMaxBlockTime;
    }

    public long getObsPollInterval() {
        return obsPollInterval;
    }

    public void setObsPollInterval(long obsPollInterval) {
        this.obsPollInterval = obsPollInterval;
    }

    public long getObsTimeout() {
        return obsTimeout;
    }

    public void setObsTimeout(long obsTimeout) {
        this.obsTimeout = obsTimeout;
    }

    public String makeBootstrapURI() {
        return protocol + "://" + hostname + ":" + port + "/pools";
    }

    private CouchbaseClient createClient() throws IOException, URISyntaxException {
        List<URI> hosts = Arrays.asList(
                new URI(makeBootstrapURI())
        );

        CouchbaseConnectionFactoryBuilder cfb = new CouchbaseConnectionFactoryBuilder();

        if (opTimeOut != DEFAULT_OP_TIMEOUT) cfb.setOpTimeout(opTimeOut);
        if (timeoutExceptionThreshold != DEFAULT_TIMEOUT_EXCEPTION_THRESHOLD) cfb.setTimeoutExceptionThreshold(timeoutExceptionThreshold);
        if (readBufferSize != DEFAULT_READ_BUFFER_SIZE) cfb.setReadBufferSize(readBufferSize);
        if (shouldOptimize) cfb.setShouldOptimize(true);
        if (maxReconnectDelay != DEFAULT_MAX_RECONNECT_DELAY) cfb.setMaxReconnectDelay(maxReconnectDelay);
        if (opQueueMaxBlockTime != DEFAULT_OP_QUEUE_MAX_BLOCK_TIME) cfb.setOpQueueMaxBlockTime(opQueueMaxBlockTime);
        if (obsPollInterval != DEFAULT_OBS_POLL_INTERVAL) cfb.setObsPollInterval(obsPollInterval);
        if (obsTimeout != DEFAULT_OBS_TIMEOUT) cfb.setObsTimeout(obsTimeout);

        return new CouchbaseClient(cfb.buildCouchbaseConnection(hosts, bucket, username, password));

    }

}