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
    private String host = "127.0.0.1";
    private int port = 5701;

    public VertxComponent() {
        super(VertxEndpoint.class);
    }

    public VertxComponent(CamelContext context) {
        super(context, VertxEndpoint.class);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Vertx getVertx() {
        return vertx;
    }

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
                LOG.debug("Creating Vertx {}:{}", host, port);
                vertx = VertxFactory.newVertx(port, host);
            } else {
                LOG.debug("Creating Vertx");
                vertx = VertxFactory.newVertx();
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.debug("Stopping Vertx {}", vertx);
        vertx.stop();
    }
}
