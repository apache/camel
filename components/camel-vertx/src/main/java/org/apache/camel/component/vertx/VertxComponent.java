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
package org.apache.camel.component.vertx;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.EndpointCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;

/**
 * A Camel Component for <a href="http://vertx.io/">vert.x</a>
 */
public class VertxComponent extends UriEndpointComponent implements EndpointCompleter {
    private static final Logger LOG = LoggerFactory.getLogger(VertxComponent.class);
    private Vertx vertx;
    private String host;
    private int port;

    public VertxComponent() {
        super(VertxEndpoint.class);
    }

    public VertxComponent(CamelContext context) {
        super(context, VertxEndpoint.class);
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname for creating an embedded clustered EventBus
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port for creating an embedded clustered EventBus
     */
    public void setPort(int port) {
        this.port = port;
    }

    public Vertx getVertx() {
        return vertx;
    }

    /**
     * To use the given vertx EventBus instead of creating a new embedded EventBus
     */
    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        VertxEndpoint endpoint = new VertxEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public List<String> completeEndpointPath(ComponentConfiguration componentConfiguration, String text) {
        // TODO is there any way to find out the list of endpoint names in vertx?
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (vertx == null) {
            // lets using a host / port if a host name is specified
            if (host != null && host.length() > 0) {
                LOG.info("Creating Clustered Vertx {}:{}", host, port);
                vertx = VertxFactory.newVertx(port, host);
            } else if (host != null) {
                LOG.info("Creating Clustered Vertx {}", host);
                vertx = VertxFactory.newVertx(host);
            } else {
                LOG.info("Creating Non-Clustered Vertx");
                vertx = VertxFactory.newVertx();
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.info("Stopping Vertx {}", vertx);
        vertx.stop();
    }
}
