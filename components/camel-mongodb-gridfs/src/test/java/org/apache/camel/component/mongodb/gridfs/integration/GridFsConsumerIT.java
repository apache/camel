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
package org.apache.camel.component.mongodb.gridfs.integration;

import java.util.HashMap;
import java.util.Map;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.mongodb.gridfs.GridFsConstants;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_KEY_FILENAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GridFsConsumerIT extends AbstractMongoDbITSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:create")
                        .to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket());
                from("direct:create-a")
                        .to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket() + "-a");
                from("direct:create-pts")
                        .to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket() + "-pts");

                from("mongodb-gridfs:myDb?database={{mongodb.testDb}}&bucket=" + getBucket()).convertBodyTo(String.class)
                        .to("mock:test");
                from("mongodb-gridfs:myDb?database={{mongodb.testDb}}&bucket=" + getBucket() + "-a&queryStrategy=FileAttribute")
                        .convertBodyTo(String.class).to("mock:test");
                from("mongodb-gridfs:myDb?database={{mongodb.testDb}}&bucket=" + getBucket()
                     + "-pts&queryStrategy=PersistentTimestamp")
                        .convertBodyTo(String.class).to("mock:test");
                from("mongodb-gridfs:myDb?database={{mongodb.testDb}}&bucket=customFileFilterTest&queryStrategy=TimeStampAndFileAttribute&query="
                     + String.format("{'%s': '%s'}", GRIDFS_FILE_KEY_FILENAME, FILE_NAME))
                        .convertBodyTo(String.class).to("mock:test");
            }
        };
    }

    @Test
    public void testTimestamp() throws Exception {
        runTest("direct:create", gridFSBucket);
    }

    @Test
    public void testAttribute() throws Exception {
        runTest("direct:create-a", GridFSBuckets.create(mongo.getDatabase("test"), getBucket() + "-a"));
    }

    @Test
    public void testPersistentTS() throws Exception {
        runTest("direct:create-pts", GridFSBuckets.create(mongo.getDatabase("test"), getBucket() + "-pts"));
    }

    @Test
    public void testCustomFileQuery() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.FILE_NAME, FILE_NAME);

        Exchange result = template.request(
                "mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=customFileFilterTest",
                new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                        exchange.getMessage().setBody(FILE_DATA);
                        exchange.getMessage().setHeaders(headers);
                    }
                });
        ObjectId objectId = result.getMessage().getHeader(GridFsConstants.GRIDFS_OBJECT_ID, ObjectId.class);
        assertNotNull(objectId);

        MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedBodiesReceived(FILE_DATA);
        mock.assertIsSatisfied();

        template.requestBodyAndHeader(
                "mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=remove&bucket=customFileFilterTest", null,
                GridFsConstants.GRIDFS_OBJECT_ID, objectId);

        Integer count = template.requestBodyAndHeaders(
                "mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=count&bucket=customFileFilterTest", null, headers,
                Integer.class);
        assertEquals(0, count);
    }

    public void runTest(String target, GridFSBucket gridfs) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedBodiesReceived(FILE_DATA);
        mock.expectedHeaderReceived(GridFsConstants.GRIDFS_METADATA, "{\"contentType\": \"text/plain\"}");

        Map<String, Object> headers = new HashMap<>();
        assertFalse(gridfs.find(eq(GRIDFS_FILE_KEY_FILENAME, FILE_NAME)).cursor().hasNext());

        headers.put(Exchange.FILE_NAME, FILE_NAME);
        headers.put(Exchange.CONTENT_TYPE, "text/plain");
        template.requestBodyAndHeaders(target, FILE_DATA, headers);

        mock.assertIsSatisfied();
        mock.reset();

        mock.expectedBodiesReceived(FILE_DATA, FILE_DATA, FILE_DATA);

        headers.put(Exchange.FILE_NAME, FILE_NAME + "_1");
        template.requestBodyAndHeaders(target, FILE_DATA, headers);
        headers.put(Exchange.FILE_NAME, FILE_NAME + "_2");
        template.requestBodyAndHeaders(target, FILE_DATA, headers);
        headers.put(Exchange.FILE_NAME, FILE_NAME + "_3");
        template.requestBodyAndHeaders(target, FILE_DATA, headers);
        mock.assertIsSatisfied();
    }
}
