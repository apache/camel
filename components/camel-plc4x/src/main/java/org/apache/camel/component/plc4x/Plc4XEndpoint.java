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
import java.util.Objects;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
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
import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.utils.connectionpool.PooledPlcDriverManager;

@UriEndpoint(scheme = "plc4x", firstVersion = "3.20.0", title = "PLC4X",
             syntax = "plc4x:driver", category = Category.IOT)
public class Plc4XEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true, description = "PLC4X connection string for the connection to the target")
    private String driver;
    @UriParam
    @Metadata(description = "The tags to read as Map<String,String> containing the tag name associated to its query")
    private Map<String, Object> tags;
    @UriParam
    @Metadata(label = "consumer",
              description = "Query to a trigger. On a rising edge of the trigger, the tags will be read once")
    private String trigger;
    @UriParam
    @Metadata(label = "consumer", description = "Interval on which the Trigger should be checked")
    private int period;

    private PlcDriverManager plcDriverManager;
    private PlcConnection connection;
    private String uri;

    public Plc4XEndpoint(String endpointUri, Component component) throws PlcConnectionException {
        super(endpointUri, component);
        this.plcDriverManager = new PlcDriverManager();
        //Here we establish the connection in the endpoint, as it is created once during the context
        // to avoid disconnecting and reconnecting for every request
        this.uri = endpointUri.replaceFirst("plc4x:/?/?", "");
        this.connection = plcDriverManager.getConnection(this.uri);
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
        plcDriverManager = new PooledPlcDriverManager();
        String plc4xURI = uri.replaceFirst("plc4x:/?/?", "");
        // TODO: is this mutation really intentional
        uri = plc4xURI;
        try {
            connection = plcDriverManager.getConnection(plc4xURI);
        } catch (PlcConnectionException e) {
            throw new PlcRuntimeException(e);
        }
    }

    public PlcConnection getConnection() {
        return connection;
    }

    @Override
    public Producer createProducer() throws Exception {
        //Checking if connection is still up and reconnecting if not
        if (!connection.isConnected()) {
            connection = plcDriverManager.getConnection(uri.replaceFirst("plc4x:/?/?", ""));
        }
        return new Plc4XProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        //Checking if connection is still up and reconnecting if not
        if (!connection.isConnected()) {
            connection = plcDriverManager.getConnection(uri.replaceFirst("plc4x:/?/?", ""));
        }
        Plc4XConsumer consumer = new Plc4XConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
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

    public Map<String, Object> getTags() {
        return tags;
    }

    public void setTags(Map<String, Object> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Plc4XEndpoint)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Plc4XEndpoint that = (Plc4XEndpoint) o;
        return Objects.equals(getDriver(), that.getDriver()) &&
                Objects.equals(getTags(), that.getTags()) &&
                Objects.equals(getPlcDriverManager(), that.getPlcDriverManager());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getDriver(), getTags(), getPlcDriverManager());
    }

    @Override
    public void doStop() throws Exception {
        //Shutting down the connection when leaving the Context
        if (connection != null && connection.isConnected()) {
            connection.close();
        }
    }

}
