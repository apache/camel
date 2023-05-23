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
package org.apache.camel.component.couchbase;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.env.ClusterEnvironment;
import org.apache.camel.CamelException;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_PUT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_URI_ERROR;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_CONNECT_TIMEOUT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_CONSUME_PROCESSED_STRATEGY;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_COUCHBASE_PORT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_DESIGN_DOCUMENT_NAME;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_PAUSE_BETWEEN_RETRIES;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_PRODUCER_RETRIES;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_QUERY_TIMEOUT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_VIEWNAME;

/**
 * Query Couchbase Views with a poll strategy and/or perform various operations against Couchbase databases.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "couchbase", title = "Couchbase", syntax = "couchbase:protocol://hostname:port",
             category = { Category.DATABASE }, headersClass = CouchbaseConstants.class)
public class CouchbaseEndpoint extends ScheduledPollEndpoint {

    @UriPath
    @Metadata(required = true)
    private String protocol;
    @UriPath
    @Metadata(required = true)
    private String hostname;
    @UriPath(defaultValue = "8091")
    private int port;

    @UriParam
    @Metadata(required = true)
    private String bucket;

    @UriParam
    private String collection;

    @UriParam
    private String scope;

    // Couchbase key
    @UriParam
    private String key;

    // Authentication
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;

    // Additional hosts
    @UriParam(label = "advanced")
    private String additionalHosts;

    // Persistence and replication parameters
    @UriParam(label = "producer", defaultValue = "0")
    private int persistTo;

    @UriParam(label = "producer", defaultValue = "0")
    private int replicateTo;

    // Producer parameters
    @UriParam(label = "producer", defaultValue = COUCHBASE_PUT)
    private String operation = COUCHBASE_PUT;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean autoStartIdForInserts;
    @UriParam(label = "producer", defaultValue = "2")
    private int producerRetryAttempts = DEFAULT_PRODUCER_RETRIES;
    @UriParam(label = "producer", defaultValue = "5000")
    private int producerRetryPause = DEFAULT_PAUSE_BETWEEN_RETRIES;

    @UriParam(label = "producer")
    private long startingIdForInsertsFrom;
    // View control
    @UriParam(label = "consumer", defaultValue = DEFAULT_DESIGN_DOCUMENT_NAME)
    private String designDocumentName = DEFAULT_DESIGN_DOCUMENT_NAME;
    @UriParam(label = "consumer", defaultValue = DEFAULT_VIEWNAME)
    private String viewName = DEFAULT_VIEWNAME;
    @UriParam(label = "consumer", defaultValue = "-1")
    private int limit = -1;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean descending;
    @UriParam(label = "consumer", defaultValue = "-1")
    private int skip = -1;
    @UriParam(label = "consumer")
    private String rangeStartKey;
    @UriParam(label = "consumer")
    private String rangeEndKey = "";
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean fullDocument = true;

    // Consumer strategy
    @UriParam(label = "consumer", defaultValue = DEFAULT_CONSUME_PROCESSED_STRATEGY)
    private String consumerProcessedStrategy = DEFAULT_CONSUME_PROCESSED_STRATEGY;

    // Connection fine tuning parameters
    @UriParam(label = "advanced", defaultValue = "2500", javaType = "java.time.Duration")
    private long queryTimeout = DEFAULT_QUERY_TIMEOUT;

    // Connection fine tuning parameters
    @UriParam(label = "advanced", defaultValue = "30000", javaType = "java.time.Duration")
    private long connectTimeout = DEFAULT_CONNECT_TIMEOUT;

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
        CouchbaseConsumer consumer = new CouchbaseConsumer(this, createClient(), processor);
        configureConsumer(consumer);
        return consumer;
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

    /**
     * The collection to use
     */
    public String getCollection() {
        return this.collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getScope() {
        return this.scope;
    }

    /**
     * The scope to use
     */
    public void setScope(String scope) {
        this.scope = scope;
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

    public boolean isFullDocument() {
        return fullDocument;
    }

    /**
     * If true consumer will return complete document instead data defined in view
     */
    public void setFullDocument(boolean fullDocument) {
        this.fullDocument = fullDocument;
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

    public long getQueryTimeout() {
        return queryTimeout;
    }

    /**
     * Define the operation timeout in milliseconds
     */
    public void setQueryTimeout(long queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Define the timeoutconnect in milliseconds
     */
    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public URI[] makeBootstrapURI() throws URISyntaxException {

        if (additionalHosts == null || additionalHosts.isEmpty()) {
            return new URI[] { new URI(protocol + "://" + hostname + ":" + port + "/pools") };
        }
        return getAllUris();

    }

    private URI[] getAllUris() throws URISyntaxException {

        String[] hosts = additionalHosts.split(",");

        for (int i = 0; i < hosts.length; i++) {
            hosts[i] = hosts[i].trim();
        }

        List<String> hostList = new ArrayList<>();
        hostList.add(hostname);
        hostList.addAll(Arrays.asList(hosts));
        Set<String> hostSet = new LinkedHashSet<>(hostList);
        hosts = hostSet.toArray(new String[0]);

        URI[] uriArray = new URI[hosts.length];

        for (int i = 0; i < hosts.length; i++) {
            uriArray[i] = new URI(protocol + "://" + hosts[i] + ":" + port + "/pools");
        }

        return uriArray;
    }

    //create from couchbase-client
    private Bucket createClient() throws Exception {
        List<URI> hosts = Arrays.asList(makeBootstrapURI());
        String connectionString;

        if (bucket == null || bucket.isEmpty()) {
            throw new CamelException(COUCHBASE_URI_ERROR);
        }

        ClusterEnvironment.Builder cfb = ClusterEnvironment.builder();
        if (queryTimeout != DEFAULT_QUERY_TIMEOUT) {
            cfb.timeoutConfig()
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .queryTimeout(Duration.ofMillis(queryTimeout));
        }

        ClusterEnvironment env = cfb.build();

        String addHosts = hosts.stream()
                .map(URI::getHost)
                .collect(Collectors.joining(","));

        if (!addHosts.isEmpty()) {
            connectionString = addHosts;
        } else {
            connectionString = hostname;
        }

        Cluster cluster = Cluster.connect(connectionString, ClusterOptions
                .clusterOptions(username, password)
                .environment(env));

        return cluster.bucket(bucket);
    }

    /**
     * Compares retry strategy with query timeout and gets the higher value : for write operations with retry
     *
     * @return
     */
    public long getWriteQueryTimeout() {
        long retryTimeout = producerRetryAttempts * (long) producerRetryPause;
        return retryTimeout > queryTimeout ? retryTimeout : queryTimeout;
    }
}
