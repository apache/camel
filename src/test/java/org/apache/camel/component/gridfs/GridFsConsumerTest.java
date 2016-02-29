/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.camel.component.gridfs;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import org.junit.Test;

/**
 * 
 */
public class GridFsConsumerTest extends AbstractMongoDbTest {
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:create").to("gridfs:myDb?database={{mongodb.testDb}}&operation=create&bucket=" + getBucket());
                from("gridfs:myDb?database={{mongodb.testDb}}&bucket=" + getBucket()).convertBodyTo(String.class).to("mock:test");
            }
        };
    }
    
    
    @Test
    public void test() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test");
        String data = "This is some stuff to go into the db";
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(data);
        
        Map<String, Object> headers = new HashMap<String, Object>();
        String fn = "filename.for.db.txt";
        assertEquals(0, gridfs.find(fn).size());
        
        headers.put(Exchange.FILE_NAME, fn);
        template.requestBodyAndHeaders("direct:create", data, headers);
        
        mock.assertIsSatisfied();
        mock.reset();
        
        mock.expectedMessageCount(3);
        mock.expectedBodiesReceived(data, data, data);
        
        headers.put(Exchange.FILE_NAME, fn + "_1");
        template.requestBodyAndHeaders("direct:create", data, headers);
        headers.put(Exchange.FILE_NAME, fn + "_2");
        template.requestBodyAndHeaders("direct:create", data, headers);
        headers.put(Exchange.FILE_NAME, fn + "_3");
        template.requestBodyAndHeaders("direct:create", data, headers);
        mock.assertIsSatisfied();
    }

}
