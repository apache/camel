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

import java.util.HashMap;
import java.util.Map;

import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.MongoDbConstants.FIELDS_PROJECTION;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.apache.camel.component.mongodb.MongoDbConstants.OPTIONS;
import static org.apache.camel.component.mongodb.MongoDbConstants.SORT_BY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbFindOneAndDeleteOperationIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    @EndpointInject("mock:test")
    private MockEndpoint mock;

    @Test
    public void testNoMatch() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().constant(null);

        Bson filter = eq("extraField", true);
        template.sendBody("direct:findOneAndDelete", filter);

        mock.assertIsSatisfied();
    }

    @Test
    public void testSimpleDelete() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Document.class);

        // insert two elements and delete one
        String connes = "{\"_id\":\"1\", \"scientist\":\"Connes\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(connes));
        String serre = "{\"_id\":\"2\", \"scientist\":\"Serre\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(serre));

        Bson filter = eq("scientist", "Serre");
        Document doc = template.requestBody("direct:findOneAndDelete", filter, Document.class);

        assertEquals("2", doc.getString(MONGO_ID), "ID does not match");
        assertEquals(0, testCollection.countDocuments(new Document("scientist", "Serre")),
                "Serre should have been deleted");
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Connes")),
                "Connes should not have been deleted");
        mock.assertIsSatisfied();
    }

    @Test
    public void testDeleteSortBy() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Document.class);

        // When the filter matches several elements, use sortBy to delete the last one
        String connes = "{\"_id\":\"1\", \"scientist\":\"Connes\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(connes));
        String serre = "{\"_id\":\"2\", \"scientist\":\"Serre\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(serre));

        Bson filter = eq("fixedField", "fixedValue");
        Document doc = template.requestBodyAndHeader("direct:findOneAndDelete",
                filter, SORT_BY, Sorts.descending("_id"), Document.class);

        mock.assertIsSatisfied();
        assertEquals("2", doc.getString(MONGO_ID), "ID does not match");
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Connes")),
                "Connes should be present.");
        assertEquals(0, testCollection.countDocuments(new Document("scientist", "Serre")),
                "Serre should be deleted.");
    }

    @Test
    public void testDeleteProjection() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Document.class);

        String connes = "{\"_id\":\"1\", \"scientist\":\"Connes\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(connes));

        Bson filter = eq("scientist", "Connes");
        Document doc = template.requestBodyAndHeader("direct:findOneAndDelete",
                filter, FIELDS_PROJECTION, Projections.include("scientist"), Document.class);

        mock.assertIsSatisfied();
        assertTrue(doc.containsKey("scientist"), "Projection is set to include scientist field");
        assertFalse(doc.containsKey("extraField"), "Projection is set to only include scientist field");
    }

    @Test
    public void testDeleteOptions() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Document.class);

        // When the filter matches several elements, use sortBy to delete the last one
        String connes = "{\"_id\":\"1\", \"scientist\":\"Connes\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(connes));
        String serre = "{\"_id\":\"2\", \"scientist\":\"Serre\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(serre));

        Bson filter = eq("fixedField", "fixedValue");
        final Map<String, Object> headers = new HashMap<>();
        // sort by id
        headers.put(SORT_BY, Sorts.ascending("_id"));
        // make sure Options take precedence
        FindOneAndDeleteOptions options = new FindOneAndDeleteOptions();
        options.sort(Sorts.descending("_id"));
        headers.put(OPTIONS, options);
        Document doc = template.requestBodyAndHeaders("direct:findOneAndDelete", filter, headers, Document.class);

        mock.assertIsSatisfied();
        assertEquals("2", doc.getString(MONGO_ID), "ID does not match");
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Connes")),
                "Connes should be present.");
        assertEquals(0, testCollection.countDocuments(new Document("scientist", "Serre")),
                "Serre should be deleted.");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:findOneAndDelete")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findOneAndDelete")
                        .to(mock);
            }
        };
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }
}
