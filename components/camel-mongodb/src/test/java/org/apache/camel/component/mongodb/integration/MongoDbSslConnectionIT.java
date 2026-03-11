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
package org.apache.camel.component.mongodb.integration;

import javax.net.ssl.SSLContext;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbComponent;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.apache.camel.test.infra.mongodb.services.MongoDBLocalContainerTLSService;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that validates TLS connectivity to MongoDB using Camel's SSLContextParameters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MongoDbSslConnectionIT implements ConfigurableContext, ConfigurableRoute {

    private static final String DATABASE = "test";
    private static final String COLLECTION = "camelTest";

    @Order(1)
    @RegisterExtension
    static MongoDBLocalContainerTLSService service = new MongoDBLocalContainerTLSService();

    @Order(2)
    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private MongoClient mongo;
    private MongoCollection<Document> testCollection;

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) throws Exception {
        SSLContextParameters sslContextParameters = createSslContextParameters();
        context.getRegistry().bind("sslContextParameters", sslContextParameters);

        SSLContext sslContext = sslContextParameters.createSSLContext(context);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(service.getReplicaSetUrl()))
                .applyToSslSettings(builder -> {
                    builder.enabled(true);
                    builder.context(sslContext);
                    builder.invalidHostNameAllowed(true);
                })
                .build();
        mongo = MongoClients.create(settings);

        MongoDatabase db = mongo.getDatabase(DATABASE);
        testCollection = db.getCollection(COLLECTION, Document.class);
        testCollection.drop();
        testCollection = db.getCollection(COLLECTION, Document.class);

        context.getComponent("mongodb", MongoDbComponent.class).setMongoConnection(null);
        context.getRegistry().bind("myDb", mongo);
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                String baseUri = String.format(
                        "mongodb:myDb?hosts=%s&database=%s&collection=%s"
                                               + "&sslContextParameters=#sslContextParameters"
                                               + "&tlsAllowInvalidHostnames=true",
                        service.getConnectionAddress(), DATABASE, COLLECTION);

                from("direct:insert").to(baseUri + "&operation=insert");
                from("direct:count").to(baseUri + "&operation=count");
            }
        });
    }

    @Test
    public void testInsertOverTls() {
        ProducerTemplate template = contextExtension.getProducerTemplate();

        Document doc = new Document("scientist", "Einstein").append("tls", true);
        Object result = template.requestBody("direct:insert", doc);
        assertNotNull(result, "Insert result should not be null");

        assertEquals(1L, testCollection.countDocuments(),
                "Test collection should contain 1 document after insert");
    }

    @Test
    public void testCountOverTls() {
        ProducerTemplate template = contextExtension.getProducerTemplate();

        Object result = template.requestBody("direct:count", "irrelevantBody");
        assertTrue(result instanceof Long, "Count result should be of type Long");
    }

    @AfterEach
    void cleanCollection() {
        if (testCollection != null) {
            testCollection.drop();
        }
    }

    @AfterAll
    void cleanup() {
        if (mongo != null) {
            mongo.close();
        }
    }

    private SSLContextParameters createSslContextParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("org/apache/camel/test/infra/mongodb/services/ssl/ca-truststore.jks");
        ksp.setPassword("changeit");

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }
}
