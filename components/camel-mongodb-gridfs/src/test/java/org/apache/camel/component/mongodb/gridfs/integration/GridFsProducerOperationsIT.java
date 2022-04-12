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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.gridfs.GridFsConstants;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GridFsProducerOperationsIT extends AbstractMongoDbITSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:create")
                        .to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket());
                from("direct:remove")
                        .to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=remove&bucket=" + getBucket());
                from("direct:findOne")
                        .to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=findOne&bucket=" + getBucket());
                from("direct:listAll")
                        .to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=listAll&bucket=" + getBucket());
                from("direct:count")
                        .setHeader(GridFsConstants.GRIDFS_OPERATION, constant("count"))
                        .to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&bucket=" + getBucket());
            }
        };
    }

    @Test
    public void testOperations() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        assertFalse(gridFSBucket.find(eq(FILE_NAME)).cursor().hasNext());

        headers.put(Exchange.FILE_NAME, FILE_NAME);
        headers.put(Exchange.CONTENT_TYPE, "text/plain");
        template.requestBodyAndHeaders("direct:create", FILE_DATA, headers);
        assertTrue(gridFSBucket.find(eq(GridFsConstants.GRIDFS_FILE_KEY_FILENAME, FILE_NAME)).cursor().hasNext());
        assertEquals(1, template.requestBodyAndHeaders("direct:count", null, headers, Long.class).longValue());
        Exchange result = template.request("direct:findOne", exchange -> exchange.getMessage().setHeaders(headers));
        assertTrue(result.getMessage().getHeader(Exchange.FILE_LENGTH, Long.class) > 0);
        assertNotNull(result.getMessage().getHeader(Exchange.FILE_LAST_MODIFIED));

        InputStream ins = result.getMessage().getBody(InputStream.class);
        assertNotNull(ins);
        byte b[] = new byte[2048];
        int i = ins.read(b);
        assertEquals(FILE_DATA, new String(b, 0, i, StandardCharsets.UTF_8));

        headers.put(Exchange.FILE_NAME, "2-" + FILE_NAME);
        headers.put(GridFsConstants.GRIDFS_CHUNKSIZE, 10);
        headers.put(GridFsConstants.GRIDFS_METADATA, "{'foo': 'bar'}");

        template.requestBodyAndHeaders("direct:create", FILE_DATA + "data2", headers);
        assertEquals(1, template.requestBodyAndHeaders("direct:count", null, headers, Long.class).longValue());
        assertEquals(2, template.requestBody("direct:count", null, Long.class).longValue());

        String s = template.requestBody("direct:listAll", null, String.class);
        assertTrue(s.contains("2-" + FILE_NAME));
        template.requestBodyAndHeaders("direct:remove", null, headers);
        assertEquals(1, template.requestBody("direct:count", null, Long.class).longValue());
        s = template.requestBodyAndHeader("direct:listAll", null, Exchange.FILE_NAME, "2-" + FILE_NAME, String.class);
        assertFalse(s.contains("2-" + FILE_NAME));
    }

    @Test
    public void testRemoveByObjectId() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.FILE_NAME, FILE_NAME);

        Exchange result = template.request(
                "mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket(), new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                        exchange.getMessage().setBody(FILE_DATA);
                        exchange.getMessage().setHeaders(headers);
                    }
                });
        ObjectId objectId = result.getMessage().getHeader(GridFsConstants.GRIDFS_OBJECT_ID, ObjectId.class);
        assertNotNull(objectId);

        template.requestBodyAndHeader("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=remove&bucket=" + getBucket(),
                null, GridFsConstants.GRIDFS_OBJECT_ID, objectId);

        Integer count = template.requestBodyAndHeaders(
                "mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=count&bucket=" + getBucket(), null, headers,
                Integer.class);
        assertEquals(0, count);
    }
}
