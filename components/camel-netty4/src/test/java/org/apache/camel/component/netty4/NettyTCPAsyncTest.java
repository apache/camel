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
package org.apache.camel.component.netty4;

import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.IOHelper;

import org.junit.Test;

public class NettyTCPAsyncTest extends BaseNettyTest {
    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    private void sendFile(String uri) throws Exception {
        template.send(uri, new Processor() {
            public void process(Exchange exchange) throws Exception {
                // Read from an input stream
                InputStream is = IOHelper.buffered(new FileInputStream("src/test/resources/test.txt"));

                byte buffer[] = IOConverter.toBytes(is);
                is.close();

                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(buffer);
            }
        });
    }

    @Test
    public void testTCPInOnlyWithNettyConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        sendFile("netty4:tcp://localhost:{{port}}?sync=false");
        
        mock.assertIsSatisfied();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4:tcp://localhost:{{port}}?sync=false")
                    .to("log:result")
                    .to("mock:result");                
            }
        };
    }

}
