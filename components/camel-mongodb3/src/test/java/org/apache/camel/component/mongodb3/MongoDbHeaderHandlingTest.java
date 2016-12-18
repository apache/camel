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
package org.apache.camel.component.mongodb3;

import com.mongodb.client.result.UpdateResult;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.junit.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb3.MongoDbConstants.MONGO_ID;

public class MongoDbHeaderHandlingTest extends AbstractMongoDbTest {

    @Test
    public void testInHeadersTransferredToOutOnCount() {
        // a read operation
        assertEquals(0, testCollection.count());
        Exchange result = template.request("direct:count", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("irrelevant body");
                exchange.getIn().setHeader("abc", "def");
            }
        });
        assertTrue("Result is not of type Long", result.getOut().getBody() instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result.getOut().getBody());
        assertEquals("An input header was not returned", "def", result.getOut().getHeader("abc"));
    }

    @Test
    public void testInHeadersTransferredToOutOnInsert() {
        Exchange result = template.request("direct:insert", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("{\"_id\":\"testInsertString\", \"scientist\":\"Einstein\"}");
                exchange.getIn().setHeader("abc", "def");
            }
        });

        // TODO: WriteResult isn't return when inserting
        // assertTrue(result.getOut().getBody() instanceof WriteResult);
        assertEquals("An input header was not returned", "def", result.getOut().getHeader("abc"));
        Document b = testCollection.find(eq(MONGO_ID, "testInsertString")).first();
        assertNotNull("No record with 'testInsertString' _id", b);
    }

    @Test
    public void testWriteResultAsHeaderWithWriteOp() {
        // Prepare test
        assertEquals(0, testCollection.count());
        Object[] req = new Object[] {new Document(MONGO_ID, "testSave1").append("scientist", "Einstein").toJson(),
                                     new Document(MONGO_ID, "testSave2").append("scientist", "Copernicus").toJson()};
        // Object result =
        template.requestBody("direct:insert", req);
        // assertTrue(result instanceof WriteResult);
        assertEquals("Number of records persisted must be 2", 2, testCollection.count());

        // Testing the save logic
        final Document record1 = testCollection.find(eq(MONGO_ID, "testSave1")).first();
        assertEquals("Scientist field of 'testSave1' must equal 'Einstein'", "Einstein", record1.get("scientist"));
        record1.put("scientist", "Darwin");

        // test that as a payload, we get back exactly our input, but enriched
        // with the CamelMongoDbWriteResult header
        Exchange resultExch = template.request("direct:save", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(record1);
            }
        });
        assertTrue(resultExch.getOut().getBody() instanceof Document);
        assertTrue(resultExch.getOut().getBody().equals(record1));
        assertTrue(resultExch.getOut().getHeader(MongoDbConstants.WRITERESULT) instanceof UpdateResult);

        Document record2 = testCollection.find(eq(MONGO_ID, "testSave1")).first();
        assertEquals("Scientist field of 'testSave1' must equal 'Darwin' after save operation", "Darwin", record2.get("scientist"));

    }

    @Test
    public void testWriteResultAsHeaderWithReadOp() {
        Exchange resultExch = template.request("direct:getDbStats", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("irrelevantBody");
                exchange.getIn().setHeader("abc", "def");
            }
        });
        assertTrue(resultExch.getOut().getBody() instanceof Document);
        assertNull(resultExch.getOut().getHeader(MongoDbConstants.WRITERESULT));
        assertEquals("def", resultExch.getOut().getHeader("abc"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                // tested routes
                from("direct:count").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true");
                from("direct:save").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save&writeResultAsHeader=true");
                from("direct:getDbStats").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=getDbStats&writeResultAsHeader=true");

                // supporting routes
                from("direct:insert").to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");

            }
        };
    }
}
