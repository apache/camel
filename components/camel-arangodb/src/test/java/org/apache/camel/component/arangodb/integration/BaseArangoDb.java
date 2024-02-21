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
package org.apache.camel.component.arangodb.integration;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.arangodb.services.ArangoDBService;
import org.apache.camel.test.infra.arangodb.services.ArangoDBServiceFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

/*
 * Note: the ArangoDB container requires a higher limit of open files than usually configured
 * by default (1024). If they start failing on CI systems, this is likely the reason.
 */
public abstract class BaseArangoDb implements ConfigurableRoute, CamelTestSupportHelper {

    @Order(1)
    @RegisterExtension
    public static ArangoDBService service = ArangoDBServiceFactory.createService();
    @Order(2)
    @RegisterExtension
    public static final CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    protected static final String DATABASE_NAME = "dbTest";
    protected static final String COLLECTION_NAME = "camelTest";
    protected static final String GRAPH_NAME = "graphTest";
    protected static final String VERTEX_COLLECTION_NAME = "vertexTest";
    protected static final String EDGE_COLLECTION_NAME = "edgeTest";
    protected ArangoDB arangoDb;
    protected ArangoDatabase arangoDatabase;
    protected ArangoCollection collection;
    protected ProducerTemplate template;

    @BeforeEach
    void beforeEach() {
        this.template = getCamelContextExtension().getProducerTemplate();
    }

    @ContextFixture
    public void createCamelContext(CamelContext ctx) {
        arangoDb = new ArangoDB.Builder().host(service.getHost(), service.getPort()).build();

        // drop any existing database to start clean
        if (arangoDb.getDatabases().contains(DATABASE_NAME)) {
            arangoDatabase = arangoDb.db(DATABASE_NAME);
            arangoDatabase.drop();
        }

        arangoDb.createDatabase(DATABASE_NAME);
        arangoDatabase = arangoDb.db(DATABASE_NAME);
        arangoDatabase.createCollection(COLLECTION_NAME);
        collection = arangoDatabase.collection(COLLECTION_NAME);

        ctx.getRegistry().bind("arangoDB", arangoDb);
        ctx.getPropertiesComponent().setLocation("classpath:arango.test.properties");
    }

    @Override
    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();

        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    protected abstract RouteBuilder createRouteBuilder() throws Exception;
}
