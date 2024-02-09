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
package org.apache.camel.component.kudu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.kudu.client.KuduClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with <a href="https://kudu.apache.org/">Apache Kudu</a>, a free and open source column-oriented data store
 * of the Apache Hadoop ecosystem.
 */
@UriEndpoint(firstVersion = "3.0",
             scheme = "kudu",
             title = "Kudu", syntax = "kudu:host:port/tableName",
             category = { Category.DATABASE, Category.IOT, Category.CLOUD }, producerOnly = true,
             headersClass = KuduConstants.class)
public class KuduEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(KuduEndpoint.class);
    private KuduClient kuduClient;
    private boolean userManagedClient;

    @UriPath(name = "host", displayName = "Host", label = "common", description = "Host of the server to connect to")
    private String host;

    @UriPath(name = "port", displayName = "Port", label = "common", description = "Port of the server to connect to")
    private String port;

    @UriParam(description = "Operation to perform")
    private KuduOperations operation;

    @UriPath(name = "tableName", displayName = "Table Name", label = "common", description = "Table to connect to")
    private String tableName;

    public KuduEndpoint(String uri, KuduComponent component) {
        super(uri, component);
        Pattern p = Pattern.compile("^(\\S+)\\:(\\d+)\\/(\\S+)$");
        Matcher m = p.matcher(uri);

        if (!m.matches()) {
            throw new RuntimeException("Unrecognizable url: " + uri);
        }

        this.setHost(m.group(1));
        this.setPort(m.group(2));
        this.setTableName(m.group(3));
    }

    @Override
    protected void doStart() throws Exception {
        LOG.trace("Connection: {}, {}", getHost(), getPort());

        //To facilitate tests, if the client is already created, do not recreate.
        if (this.getKuduClient() == null) {
            setKuduClient(new KuduClient.KuduClientBuilder(getHost() + ":" + getPort()).build());
        }
        LOG.debug("Resolved the host with the name {} as {}", getHost(), getKuduClient());
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        // Only shut down clients created by this endpoint
        if (!isUserManagedClient()) {
            KuduClient client = getKuduClient();
            if (client != null) {
                LOG.debug("Shutting down kudu client");
                try {
                    client.shutdown();
                } catch (Exception e) {
                    LOG.error("Unable to shutdown kudu client", e);
                }
            }
        }

        super.doStop();
    }

    public String getHost() {
        return host;
    }

    /**
     * Kudu master to connect to
     */
    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public KuduClient getKuduClient() {
        return kuduClient;
    }

    /**
     * Set the client to connect to a kudu resource
     */
    public void setKuduClient(KuduClient kuduClient) {
        this.kuduClient = kuduClient;
    }

    /**
     * Port where kudu service is listening
     */
    public void setPort(String port) {
        this.port = port;
    }

    @Override
    public Producer createProducer() {
        return new KuduProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("You cannot create consumers on this endpoint");
    }

    public String getTableName() {
        return tableName;
    }

    /**
     * The name of the table where the rows are stored
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public KuduOperations getOperation() {
        return operation;
    }

    /**
     * What kind of operation is to be performed in the table
     */
    public void setOperation(KuduOperations operation) {
        this.operation = operation;
    }

    public boolean isUserManagedClient() {
        return userManagedClient;
    }

    public void setUserManagedClient(boolean userManagedClient) {
        this.userManagedClient = userManagedClient;
    }
}
