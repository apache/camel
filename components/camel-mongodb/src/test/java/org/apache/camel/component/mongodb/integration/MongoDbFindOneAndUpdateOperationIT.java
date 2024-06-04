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

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
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
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.currentTimestamp;
import static com.mongodb.client.model.Updates.set;
import static org.apache.camel.component.mongodb.MongoDbConstants.CRITERIA;
import static org.apache.camel.component.mongodb.MongoDbConstants.FIELDS_PROJECTION;
import static org.apache.camel.component.mongodb.MongoDbConstants.OPTIONS;
import static org.apache.camel.component.mongodb.MongoDbConstants.RETURN_DOCUMENT;
import static org.apache.camel.component.mongodb.MongoDbConstants.SORT_BY;
import static org.apache.camel.component.mongodb.MongoDbConstants.UPSERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbFindOneAndUpdateOperationIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    @EndpointInject("mock:test")
    private MockEndpoint mock;

    @Test
    public void testNoMatch() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().constant(null);

        Bson filter = eq("extraField", true);
        Bson update = combine(set("scientist", "Darwin"), currentTimestamp("lastModified"));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        template.sendBodyAndHeaders("direct:findOneAndUpdate", update, headers);

        mock.assertIsSatisfied();
        assertEquals(0, testCollection.countDocuments(), "Upsert was set to false, no new document should have been created.");
    }

    @Test
    public void testInsertOnUpdate() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().constant(null);

        Bson filter = eq("extraField", true);
        Bson update = combine(set("scientist", "Darwin"), currentTimestamp("lastModified"));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // insert document if not found
        headers.put(UPSERT, true);

        template.sendBodyAndHeaders("direct:findOneAndUpdate", update, headers);
        mock.assertIsSatisfied();
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Darwin")),
                "Upsert was set to true, a new document should have been created.");
    }

    @Test
    public void testInsertOnUpdateBodyParams() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().constant(null);

        Bson filter = eq("extraField", true);
        Bson update = combine(set("scientist", "Darwin"), currentTimestamp("lastModified"));

        // Sends the filter and update parameter in the body
        template.sendBodyAndHeader("direct:findOneAndUpdate", new Bson[] { filter, update }, UPSERT, true);
        mock.assertIsSatisfied();
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Darwin")),
                "Upsert was set to true, a new document should have been created.");
    }

    @Test
    public void testInsertOnUpdateAndReturnUpdatedDocument() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Document.class);

        Bson filter = eq("extraField", true);
        Bson update = combine(set("scientist", "Darwin"), currentTimestamp("lastModified"));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // insert document if not found
        headers.put(UPSERT, true);
        // return document after update (in our case after insert)
        headers.put(RETURN_DOCUMENT, ReturnDocument.AFTER);

        template.sendBodyAndHeaders("direct:findOneAndUpdate", update, headers);
        mock.assertIsSatisfied();
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Darwin")),
                "Upsert was set to true, a new document should have been created.");
    }

    @Test
    public void testInsertOnUpdateSortBy() throws InterruptedException {
        mock.expectedMessageCount(1);

        // insert some documents and use sort by to update only the first element
        String connes = "{\"_id\":\"1\", \"scientist\":\"Connes\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(connes));
        String serre = "{\"_id\":\"2\", \"scientist\":\"Serre\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(serre));

        Bson filter = eq("fixedField", "fixedValue");
        Bson update = combine(set("scientist", "Dirac"), currentTimestamp("lastModified"));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // sort by id
        headers.put(SORT_BY, Sorts.ascending("_id"));

        template.sendBodyAndHeaders("direct:findOneAndUpdate", update, headers);
        mock.assertIsSatisfied();
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Dirac")),
                "Update should have happened, Dirac should be present.");
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Serre")),
                "Serre should still be present as his ID is higher.");
        assertEquals(0, testCollection.countDocuments(new Document("scientist", "Connes")),
                "Connes should have been updated to Dirac.");
    }

    @Test
    public void testInsertOnUpdateProjection() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Document.class);

        Bson filter = eq("extraField", true);
        Bson update = combine(set("scientist", "Darwin"), currentTimestamp("lastModified"));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // insert document if not found
        headers.put(UPSERT, true);
        // return only scientist field
        headers.put(FIELDS_PROJECTION, Projections.include("scientist"));
        // return document after update (in our case after insert)
        headers.put(RETURN_DOCUMENT, ReturnDocument.AFTER);

        Document doc = template.requestBodyAndHeaders("direct:findOneAndUpdate", update, headers, Document.class);
        mock.assertIsSatisfied();
        assertTrue(doc.containsKey("scientist"), "Projection is set to include scientist field");
        assertFalse(doc.containsKey("extraField"), "Projection is set to only include scientist field");
    }

    @Test
    public void testFindOneAndUpdateOptions() throws InterruptedException {
        mock.expectedMessageCount(1);

        // insert some documents and use sort by to update only the first element
        String connes = "{\"_id\":\"1\", \"scientist\":\"Connes\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(connes));
        String serre = "{\"_id\":\"2\", \"scientist\":\"Serre\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(serre));

        Bson filter = eq("fixedField", "fixedValue");
        Bson update = combine(set("scientist", "Dirac"), currentTimestamp("lastModified"));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // Let's check that the correct option is taken into account
        headers.put(SORT_BY, Sorts.descending("_id"));
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.sort(Sorts.ascending("_id"));
        headers.put(OPTIONS, options);

        template.sendBodyAndHeaders("direct:findOneAndUpdate", update, headers);
        mock.assertIsSatisfied();
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Dirac")),
                "Update should have happened, Dirac should be present.");
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Serre")),
                "Serre should still be present as his ID is higher.");
        assertEquals(0, testCollection.countDocuments(new Document("scientist", "Connes")),
                "Connes should have been updated to Dirac.");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:findOneAndUpdate")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findOneAndUpdate")
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
