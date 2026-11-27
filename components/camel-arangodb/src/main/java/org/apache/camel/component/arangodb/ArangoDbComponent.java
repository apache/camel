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
package org.apache.camel.component.arangodb;

import java.util.Map;

import com.arangodb.ArangoDB;
import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("arangodb")
public class ArangoDbComponent extends DefaultComponent {

    @Metadata(label = "advanced", autowired = true,
              description = "To use a custom com.arangodb.ArangoDB client for all the endpoints of the component. When it is"
                            + " set or autowired, the host, port, user, password and vertx options are not used, and the"
                            + " endpoints do not shut the client down.")
    private ArangoDB arangoDB;
    @Metadata(label = "advanced", autowired = true,
              description = "To use a custom io.vertx.core.Vertx instance for the HTTP connections of the ArangoDB clients that"
                            + " the endpoints create. If not set, the ArangoDB driver creates its own Vert.x instance. Not used"
                            + " when the arangoDB option sets a client.")
    private Vertx vertx;
    @Metadata(description = "The shared configuration of the endpoints. Each endpoint starts from a copy of it, and the options"
                            + " of the endpoint URI replace its values.")
    private ArangoDbConfiguration configuration = new ArangoDbConfiguration();

    public ArangoDbComponent() {
        this(null);
    }

    public ArangoDbComponent(CamelContext context) {
        super(context);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Database name must be specified.");
        }

        final ArangoDbConfiguration configurationClone
                = this.configuration != null ? this.configuration.copy() : new ArangoDbConfiguration();
        configurationClone.setDatabase(remaining);
        ArangoDbEndpoint endpoint = new ArangoDbEndpoint(uri, this, configurationClone);
        endpoint.setArangoDB(arangoDB);
        endpoint.setVertx(vertx);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public ArangoDB getArangoDB() {
        return arangoDB;
    }

    public void setArangoDB(ArangoDB arangoDB) {
        this.arangoDB = arangoDB;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public ArangoDbConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ArangoDbConfiguration configuration) {
        this.configuration = configuration;
    }
}
