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
package org.apache.camel.component.plc4x;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcIncompatibleDatatypeException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.utils.connectionpool.PooledPlcDriverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read and write to PLC devices
 */
@UriEndpoint(scheme = "plc4x", firstVersion = "3.20.0", title = "PLC4X",
             syntax = "plc4x:driver", category = Category.IOT)
public class Plc4XEndpoint extends DefaultEndpoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(Plc4XEndpoint.class);

    protected PlcDriverManager plcDriverManager;
    protected PlcConnection connection;

    @UriPath
    @Metadata(required = true, description = "PLC4X connection string for the connection to the target")
    private String driver;
    @UriParam(label = "consumer", prefix = "tag.", multiValue = true)
    @Metadata(description = "Tags as key/values from the Map to use in query")
    private Map<String, String> tags;
    @UriParam
    @Metadata(label = "consumer",
              description = "Query to a trigger. On a rising edge of the trigger, the tags will be read once")
    private String trigger;
    @UriParam
    @Metadata(label = "consumer", description = "Interval on which the Trigger should be checked")
    private int period;
    @UriParam
    @Metadata(description = "Whether to reconnect when no connection is present upon doing a request")
    private boolean autoReconnect;

    private String uri;

    public Plc4XEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        this.plcDriverManager = new PlcDriverManager();
        this.uri = endpointUri.replaceFirst("plc4x:/?/?", "");
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public String getUri() {
        return uri;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
        this.plcDriverManager = new PooledPlcDriverManager();
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    /**
     * Set up the connection.
     *
     * @throws PlcConnectionException if no connection could be established and auto-reconnect is turned off
     */
    public void setupConnection() throws PlcConnectionException {
        try {
            connection = plcDriverManager.getConnection(this.uri);
            if (!connection.isConnected()) {
                reconnectIfNeeded();
            }
        } catch (PlcConnectionException e) {
            if (isAutoReconnect()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.warn("Could not connect during setup, retrying on next request", e);
                } else {
                    LOGGER.warn("Could not connect during setup, retrying on next request");
                }
            } else {
                LOGGER.warn("Could not connect during setup and auto reconnect is turned off");
                throw e;
            }
        }
    }

    /**
     * Reconnects if needed. If connection is lost and auto-reconnect is turned off, endpoint will be shutdown.
     * <p>
     *
     * @throws PlcConnectionException If reconnect failed and auto-reconnect is turned on
     */
    public void reconnectIfNeeded() throws PlcConnectionException {
        if (connection != null && connection.isConnected()) {
            LOGGER.trace("No reconnect needed, already connected");
        } else if (autoReconnect && connection == null) {
            connection = plcDriverManager.getConnection(uri);
            LOGGER.debug("Successfully reconnected");
        } else if (autoReconnect && !connection.isConnected()) {
            connection.connect();
            // If reconnection fails without Exception, reset connection
            if (!connection.isConnected()) {
                LOGGER.debug("No connection established after connect, resetting connection");
                connection = plcDriverManager.getConnection(uri);
            }
            LOGGER.debug("Successfully reconnected");
        } else {
            LOGGER.warn("Connection lost and auto-reconnect is turned off, shutting down Plc4XEndpoint");
            stop();
        }
    }

    /**
     * @return true if connection supports writing, else false
     */
    public boolean canWrite() {
        return connection.getMetadata().canWrite();
    }

    @Override
    public Producer createProducer() {
        return new Plc4XProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Plc4XConsumer consumer = new Plc4XConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public PollingConsumer createPollingConsumer() {
        return new Plc4XPollingConsumer(this);
    }

    /**
     * Build a {@link PlcReadRequest} using the tags specified in the endpoint.
     */
    public PlcReadRequest buildPlcReadRequest() {
        PlcReadRequest.Builder builder = connection.readRequestBuilder();
        if (tags != null) {
            for (Map.Entry<String, String> tag : tags.entrySet()) {
                try {
                    builder.addItem(tag.getKey(), tag.getValue());
                } catch (PlcIncompatibleDatatypeException e) {
                    LOGGER.warn("For consumer, please use Map<String,String>, currently using {}",
                            tags.getClass().getSimpleName());
                }
            }
        }
        return builder.build();
    }

    /**
     * Build a {@link PlcWriteRequest}.
     * <p>
     *
     * @param  tags tags to add to write request
     * @return      {@link PlcWriteRequest}
     */
    public PlcWriteRequest buildPlcWriteRequest(Map<String, Map<String, Object>> tags) {
        PlcWriteRequest.Builder builder = connection.writeRequestBuilder();

        for (Map.Entry<String, Map<String, Object>> entry : tags.entrySet()) {
            //Tags are stored like this --> Map<Tagname,Map<Query,Value>> for writing
            String name = entry.getKey();
            String query = entry.getValue().keySet().iterator().next();
            Object value = entry.getValue().get(query);
            builder.addItem(name, query, value);
        }
        return builder.build();
    }

    public PlcDriverManager getPlcDriverManager() {
        return plcDriverManager;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    @Override
    public void doStop() throws Exception {
        //Shutting down the connection when leaving the Context
        if (connection != null && connection.isConnected()) {
            connection.close();
            connection = null;
        }
    }

}
