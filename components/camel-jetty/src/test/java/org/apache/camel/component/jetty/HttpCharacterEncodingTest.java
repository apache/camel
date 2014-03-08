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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class HttpCharacterEncodingTest extends BaseJettyTest {
    
    @Test
    public void testSendToJetty() throws Exception {
        Exchange exchange = template.send("http://localhost:{{port}}/myapp/myservice", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World Thai Elephant \u0E08");
                exchange.getIn().setHeader("Content-Type", "text/html; charset=utf-8");
            }
                                        
        });
        // convert the response to a String
        String body = exchange.getOut().getBody(String.class);
        assertEquals("Response message is Thai Elephant \u0E08", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/myservice").process(new MyBookService());
            }
        };
    }

    public class MyBookService implements Processor {
        public void process(Exchange exchange) throws Exception {
            // just get the body as a string
            String body = exchange.getIn().getBody(String.class);
          
            // for unit testing make sure we got right message
            assertEquals("Hello World Thai Elephant \u0E08", body);
            
            // send a html response
            exchange.getOut().setHeader("Content-Type", "text/html; charset=utf-8");
            exchange.getOut().setBody("Response message is Thai Elephant \u0E08");
        }
    }

}
