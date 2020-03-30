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
package org.apache.camel.component.mongodb.gridfs;

import java.util.HashMap;
import java.util.Map;

import com.mongodb.gridfs.GridFS;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class GridFsConsumerTest extends AbstractMongoDbTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:create").to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket());
                from("direct:create-a").to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket() + "-a");
                from("direct:create-pts").to("mongodb-gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket() + "-pts");
                
                from("mongodb-gridfs:myDb?database={{mongodb.testDb}}&bucket=" + getBucket()).convertBodyTo(String.class).to("mock:test");
                from("mongodb-gridfs:myDb?database={{mongodb.testDb}}&bucket=" + getBucket() + "-a&queryStrategy=FileAttribute")
                    .convertBodyTo(String.class).to("mock:test");
                from("mongodb-gridfs:myDb?database={{mongodb.testDb}}&bucket=" + getBucket() + "-pts&queryStrategy=PersistentTimestamp")
                    .convertBodyTo(String.class).to("mock:test");
            }
        };
    }
    
    
    @Test
    public void testTimestamp() throws Exception {
        runTest("direct:create", gridfs);
    }
    @Test
    @SuppressWarnings("deprecation")
    public void testAttribute() throws Exception {
        runTest("direct:create-a", new GridFS(mongo.getDB("test"), getBucket() + "-a"));
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testPersistentTS() throws Exception {
        runTest("direct:create-pts", new GridFS(mongo.getDB("test"), getBucket() + "-pts"));
    }
    
    public void runTest(String target, GridFS gridfs) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test");
        String data = "This is some stuff to go into the db";
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(data);
        
        Map<String, Object> headers = new HashMap<>();
        String fn = "filename.for.db.txt";
        assertEquals(0, gridfs.find(fn).size());
        
        headers.put(Exchange.FILE_NAME, fn);
        template.requestBodyAndHeaders(target, data, headers);
        
        mock.assertIsSatisfied();
        mock.reset();
        
        mock.expectedMessageCount(3);
        mock.expectedBodiesReceived(data, data, data);
        
        headers.put(Exchange.FILE_NAME, fn + "_1");
        template.requestBodyAndHeaders(target, data, headers);
        headers.put(Exchange.FILE_NAME, fn + "_2");
        template.requestBodyAndHeaders(target, data, headers);
        headers.put(Exchange.FILE_NAME, fn + "_3");
        template.requestBodyAndHeaders(target, data, headers);
        mock.assertIsSatisfied();
        Thread.sleep(1000);
        mock.assertIsSatisfied();
    }

}
