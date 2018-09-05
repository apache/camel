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
package org.apache.camel.component.netty.http;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMethods;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.junit.Test;

public class NettyHttpBindingUseRelativePathInPostTest extends BaseNettyTest {

    @Test
    public void testSendToNetty() throws Exception {
        Exchange exchange = template.request("netty-http:http://localhost:{{port}}/myapp/myservice?query1=a&query2=b&useRelativePath=true", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("b1=x&b2=y");
                exchange.getIn().setHeader("content-type", "application/x-www-form-urlencoded");
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);
            }

        });
        // convert the response to a String
        String body = exchange.getOut().getBody(String.class);
        assertEquals("Request message is OK", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("netty-http:http://localhost:{{port}}/myapp/myservice").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);

                        // for unit testing make sure we got right message
                        assertEquals("The body message is wrong", "b1=x&b2=y", body);
                        assertEquals("Get a wrong query parameter from the message header", "a", exchange.getIn().getHeader("query1"));
                        assertEquals("Get a wrong query parameter from the message header", "b", exchange.getIn().getHeader("query2"));
                        assertEquals("Get a wrong form parameter from the message header", "x", exchange.getIn().getHeader("b1"));
                        assertEquals("Get a wrong form parameter from the message header", "y", exchange.getIn().getHeader("b2"));
                        assertEquals("Get a wrong form parameter from the message header", "localhost:" + getPort(), exchange.getIn().getHeader("host"));

                        UpstreamMessageEvent event = (UpstreamMessageEvent) exchange.getIn().getHeader("CamelNettyMessageEvent");
                        DefaultHttpRequest request = (DefaultHttpRequest) event.getMessage();
                        assertEquals("Relative path not used in POST", "/myapp/myservice?query1=a&query2=b", request.getUri());

                        // send a response
                        exchange.getOut().getHeaders().clear();
                        exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                        exchange.getOut().setBody("Request message is OK");
                    }
                });
            }
        };
    }

}
