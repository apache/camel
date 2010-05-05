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
package org.apache.camel.component.jetty;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;


public class HttpGZipEncodingTest extends ContextTestSupport {

    public void testHttpProducerWithGzip() throws Exception {
        InputStream response = (InputStream) template.requestBodyAndHeader("http://localhost:9081/gzip", new ByteArrayInputStream("<Hello>World</Hello>".getBytes()), "Content-Encoding", "gzip");
        assertEquals("The response is wrong", "<b>Hello World</b>", IOConverter.toString(response));
    }
    
    
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());
                
                from("jetty:http://localhost:9081/gzip").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        String request = exchange.getIn().getBody(String.class);
                        assertEquals("Get a wrong request string", "<Hello>World</Hello>", request);
                        exchange.getOut().setHeader("Content-Encoding", "gzip");
                        exchange.getOut().setBody("<b>Hello World</b>");
                    }
                    
                });
                
            }
        };
    }

}
