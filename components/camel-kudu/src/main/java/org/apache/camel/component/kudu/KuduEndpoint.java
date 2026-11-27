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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.kudu.client.KuduClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Insert, update, upsert, delete and scan rows of <a href="https://kudu.apache.org/">Apache Kudu</a> tables, and create
 * tables.
 */
@UriEndpoint(firstVersion = "3.0",
             scheme = "kudu",
             title = "Kudu", syntax = "kudu:host:port/tableName",
             category = { Category.DATABASE, Category.IOT, Category.CLOUD }, producerOnly = true,
             headersClass = KuduConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.MAP,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "SCAN", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "CREATE_TABLE",
                                                         carriers = Carrier.ANY) },
                                  description = "For insert, update, upsert and delete: a Map of the column names and"
                                                + " their values, one row, each value of the Java type of its column (a"
                                                + " String for an INT32 column fails); delete uses only the key columns."
                                                + " Text such as JSON, a List or a POJO fails: unmarshal it into a Map"
                                                + " first. Scan and create_table do not read the body: they take the"
                                                + " CamelKuduScan* and the CamelKuduSchema and CamelKuduTableOptions"
                                                + " headers. The operation must be set"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = @BodyType.When(option = "operation", value = "SCAN", carriers = Carrier.LIST,
                                                             javaType = "java.util.List<java.util.Map<String, Object>>"),
                                   description = "Scan gives a List of the rows, each a Map of the column names and their"
                                                 + " values, an empty List for no rows. The other operations leave the body"
                                                 + " unchanged"))
public class KuduEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(KuduEndpoint.class);
    private KuduClient kuduClient;
    private boolean userManagedClient;

    @UriPath(name = "host", displayName = "Host", label = "common",
             description = "The host name or IP address of the Kudu master. The endpoint creates its Kudu client for this"
                           + " host and port, unless the kuduClient component option is set.")
    @Metadata(required = true)
    private String host;

    @UriPath(name = "port", displayName = "Port", label = "common",
             description = "The RPC port of the Kudu master, which is 7051 in a default Kudu installation.")
    @Metadata(required = true)
    private String port;

    @UriParam(description = "The operation that the producer performs on the table. INSERT, UPDATE, UPSERT and DELETE write"
                            + " the one row in the Map of the message body, CREATE_TABLE creates the table from the"
                            + " CamelKuduSchema and CamelKuduTableOptions headers, and SCAN puts the rows of the table in the"
                            + " message body. There is no default, and an exchange fails when the option is not set.")
    private KuduOperations operation;

    @UriPath(name = "tableName", displayName = "Table Name", label = "common",
             description = "The name of the Kudu table that the operation reads, writes or creates.")
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
    public String getServiceUrl() {
        return host + ":" + port;
    }

    @Override
    public String getServiceProtocol() {
        return "kudu";
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

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public KuduOperations getOperation() {
        return operation;
    }

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
