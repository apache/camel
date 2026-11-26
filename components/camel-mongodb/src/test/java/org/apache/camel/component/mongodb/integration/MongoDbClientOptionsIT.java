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

import java.util.List;

import com.mongodb.ReadPreference;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbEndpoint;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The client options of an endpoint with hosts reach the MongoDB server.
 */
// public: the RouteFixture of the test-infra is called by reflection
public class MongoDbClientOptionsIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    private static final String APP_NAME = "camel-27193-it";

    private String currentOpUri() {
        return "mongodb:dummy?hosts=" + service.getConnectionAddress() + "&database=admin&operation=command&appName="
               + APP_NAME;
    }

    @Test
    void appNameIsSentToTheServer() {
        // the operations of the clients with this application name, which includes this currentOp command itself
        Document result = template.requestBody("direct:currentOp",
                "{\"currentOp\": true, \"$all\": true, \"appName\": \"" + APP_NAME + "\"}", Document.class);

        List<Document> inprog = result.getList("inprog", Document.class);
        assertThat(inprog).isNotEmpty().allSatisfy(op -> assertThat(op.getString("appName")).isEqualTo(APP_NAME));
    }

    @Test
    void readPreferenceIsUsedByTheCollection() {
        MongoDbEndpoint endpoint = context.getEndpoint(
                "mongodb:dummy?hosts=" + service.getConnectionAddress()
                                                       + "&database={{mongodb.testDb}}&collection={{mongodb.testCollection}}"
                                                       + "&operation=count&readPreference=SECONDARY_PREFERRED",
                MongoDbEndpoint.class);

        Long count = template.requestBody(endpoint, "{}", Long.class);

        assertThat(count).isZero();
        assertThat(endpoint.getMongoCollection().getReadPreference()).isEqualTo(ReadPreference.secondaryPreferred());
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:currentOp").to(currentOpUri());
            }
        });
    }
}
