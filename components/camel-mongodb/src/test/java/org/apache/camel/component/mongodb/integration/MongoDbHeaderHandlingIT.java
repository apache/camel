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

import com.mongodb.client.result.UpdateResult;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbHeaderHandlingIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    @BeforeEach
    void checkDocuments() {
        Assumptions.assumeTrue(0 == testCollection.countDocuments(), "The collection should have no documents");
    }

    @Test
    public void testInHeadersTransferredToOutOnCount() {
        // a read operation
        Exchange result = template.request("direct:count", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("irrelevant body");
                exchange.getIn().setHeader("abc", "def");
            }
        });
        assertTrue(result.getMessage().getBody() instanceof Long, "Result is not of type Long");
        assertEquals(0L, result.getMessage().getBody(), "Test collection should not contain any records");
        assertEquals("def", result.getMessage().getHeader("abc"), "An input header was not returned");
    }

    @Test
    public void testInHeadersTransferredToOutOnInsert() {
        Exchange result = template.request("direct:insert", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("{\"_id\":\"testInsertString\", \"scientist\":\"Einstein\"}");
                exchange.getIn().setHeader("abc", "def");
            }
        });

        // TODO: WriteResult isn't return when inserting
        // assertTrue(result.getOut().getBody() instanceof WriteResult);
        assertEquals("def", result.getMessage().getHeader("abc"), "An input header was not returned");
        Document b = testCollection.find(eq(MONGO_ID, "testInsertString")).first();
        assertNotNull(b, "No record with 'testInsertString' _id");
    }

    @Test
    public void testWriteResultAsHeaderWithWriteOp() {
        // Prepare test
        Object[] req = new Object[] {
                new Document(MONGO_ID, "testSave1").append("scientist", "Einstein").toJson(),
                new Document(MONGO_ID, "testSave2").append("scientist", "Copernicus").toJson() };
        // Object result =
        template.requestBody("direct:insert", req);
        // assertTrue(result instanceof WriteResult);
        assertEquals(2, testCollection.countDocuments(), "Number of records persisted must be 2");

        // Testing the save logic
        final Document record1 = testCollection.find(eq(MONGO_ID, "testSave1")).first();
        assertEquals("Einstein", record1.get("scientist"), "Scientist field of 'testSave1' must equal 'Einstein'");
        record1.put("scientist", "Darwin");

        // test that as a payload, we get back exactly our input, but enriched
        // with the CamelMongoDbWriteResult header
        Exchange resultExch = template.request("direct:save", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(record1);
            }
        });
        assertTrue(resultExch.getMessage().getBody() instanceof Document);
        assertEquals(record1, resultExch.getMessage().getBody());
        assertTrue(resultExch.getMessage().getHeader(MongoDbConstants.WRITERESULT) instanceof UpdateResult);

        Document record2 = testCollection.find(eq(MONGO_ID, "testSave1")).first();
        assertEquals("Darwin", record2.get("scientist"),
                "Scientist field of 'testSave1' must equal 'Darwin' after save operation");

    }

    @Test
    public void testWriteResultAsHeaderWithReadOp() {
        Exchange resultExch = template.request("direct:getDbStats", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("irrelevantBody");
                exchange.getIn().setHeader("abc", "def");
            }
        });
        assertTrue(resultExch.getMessage().getBody() instanceof Document);
        assertNull(resultExch.getMessage().getHeader(MongoDbConstants.WRITERESULT));
        assertEquals("def", resultExch.getMessage().getHeader("abc"));
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                // tested routes
                from("direct:count").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true");
                from("direct:save").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save&writeResultAsHeader=true");
                from("direct:getDbStats").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=getDbStats&writeResultAsHeader=true");

                // supporting routes
                from("direct:insert")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");

            }
        };
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }
}
