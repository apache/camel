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

import com.mongodb.client.model.FindOneAndReplaceOptions;
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
import static org.apache.camel.component.mongodb.MongoDbConstants.CRITERIA;
import static org.apache.camel.component.mongodb.MongoDbConstants.FIELDS_PROJECTION;
import static org.apache.camel.component.mongodb.MongoDbConstants.OPTIONS;
import static org.apache.camel.component.mongodb.MongoDbConstants.RETURN_DOCUMENT;
import static org.apache.camel.component.mongodb.MongoDbConstants.SORT_BY;
import static org.apache.camel.component.mongodb.MongoDbConstants.UPSERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbFindOneAndReplaceOperationIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    @EndpointInject("mock:test")
    private MockEndpoint mock;

    @Test
    public void testNoMatch() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().constant(null);

        Bson filter = eq("extraField", true);
        Document replacement = new Document("_id", 1);
        template.sendBodyAndHeader("direct:findOneAndReplace", replacement, CRITERIA, filter);

        mock.assertIsSatisfied();
        assertEquals(0, testCollection.countDocuments(), "Upsert was set to false, no new document should have been created.");
    }

    @Test
    public void testInsertWhenNotFound() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().constant(null);

        Bson filter = eq("extraField", true);
        Document replacement = new Document("_id", 1);
        replacement.append("scientist", "Darwin");

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // insert document if not found
        headers.put(UPSERT, true);

        template.sendBodyAndHeaders("direct:findOneAndReplace", replacement, headers);
        mock.assertIsSatisfied();
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Darwin")),
                "Upsert was set to true, a new document should have been created.");
    }

    @Test
    public void testInsertOnUpdateAndReturnUpdatedDocument() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Document.class);

        Bson filter = eq("extraField", true);
        Document replacement = new Document("_id", 1);
        replacement.append("scientist", "Darwin");

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // insert document if not found
        headers.put(UPSERT, true);
        // return document after update (in our case after insert)
        headers.put(RETURN_DOCUMENT, ReturnDocument.AFTER);

        template.sendBodyAndHeaders("direct:findOneAndReplace", replacement, headers);
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
        Document replacement = new Document();
        replacement.append("scientist", "Dirac");

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // sort by id
        headers.put(SORT_BY, Sorts.ascending("_id"));

        template.sendBodyAndHeaders("direct:findOneAndReplace", replacement, headers);
        mock.assertIsSatisfied();
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Dirac")),
                "Update should have happened, Dirac should be present.");
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Serre")),
                "Serre should still be present as his ID is higher.");
        assertEquals(0, testCollection.countDocuments(new Document("scientist", "Connes")),
                "Connes should have been replaced by Dirac.");
        assertEquals(1, testCollection.countDocuments(new Document("fixedField", "fixedValue")),
                "Connes should have been replaced by Dirac which has no fixedField");
    }

    @Test
    public void testInsertOnUpdateProjection() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Document.class);

        Bson filter = eq("extraField", true);
        Document replacement = new Document();
        replacement.append("scientist", "Dirac").append("extraField", true);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // insert document if not found
        headers.put(UPSERT, true);
        // return only scientist field
        headers.put(FIELDS_PROJECTION, Projections.include("scientist"));
        // return document after update (in our case after insert)
        headers.put(RETURN_DOCUMENT, ReturnDocument.AFTER);

        Document doc = template.requestBodyAndHeaders("direct:findOneAndReplace", replacement, headers, Document.class);
        mock.assertIsSatisfied();
        assertTrue(doc.containsKey("scientist"), "Projection is set to include scientist field");
        assertFalse(doc.containsKey("extraField"), "Projection is set to only include scientist field");
    }

    @Test
    public void testFindOneAndReplaceOptions() throws InterruptedException {
        mock.expectedMessageCount(1);

        // insert some documents and use sort by to update only the first element
        String connes = "{\"_id\":\"1\", \"scientist\":\"Connes\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(connes));
        String serre = "{\"_id\":\"2\", \"scientist\":\"Serre\", \"fixedField\": \"fixedValue\"}";
        testCollection.insertOne(Document.parse(serre));

        Bson filter = eq("fixedField", "fixedValue");
        Document replacement = new Document();
        replacement.append("scientist", "Dirac");

        final Map<String, Object> headers = new HashMap<>();
        headers.put(CRITERIA, filter);
        // sort by id
        headers.put(SORT_BY, Sorts.descending("_id"));
        FindOneAndReplaceOptions options = new FindOneAndReplaceOptions();
        options.sort(Sorts.ascending("_id"));
        headers.put(OPTIONS, options);

        template.sendBodyAndHeaders("direct:findOneAndReplace", replacement, headers);
        mock.assertIsSatisfied();
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Dirac")),
                "Update should have happened, Dirac should be present.");
        assertEquals(1, testCollection.countDocuments(new Document("scientist", "Serre")),
                "Serre should still be present as his ID is higher.");
        assertEquals(0, testCollection.countDocuments(new Document("scientist", "Connes")),
                "Connes should have been replaced by Dirac.");
        assertEquals(1, testCollection.countDocuments(new Document("fixedField", "fixedValue")),
                "Connes should have been replaced by Dirac which has no fixedField");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:findOneAndReplace")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findOneAndReplace")
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
