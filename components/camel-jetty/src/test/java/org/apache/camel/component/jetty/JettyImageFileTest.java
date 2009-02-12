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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.helper.GZIPHelper;

/**
 * Unit test for exposing a http server that returns images
 */
public class JettyImageFileTest extends ContextTestSupport {
    
    private void sendImageContent(boolean usingGZip) throws Exception {
        Endpoint endpoint = context.getEndpoint("http://localhost:9080/myapp/myservice");
        Exchange exchange = endpoint.createExchange();        
        if (usingGZip) {
            GZIPHelper.setGZIPMessageHeader(exchange.getIn());
        }
        template.send(endpoint, exchange);

        assertNotNull(exchange.getOut().getBody());
        assertOutMessageHeader(exchange, "Content-Type", "image/jpeg");
    }

    public void testImageContentType() throws Exception {
        sendImageContent(false);
    }
    
    public void testImageContentWithGZip() throws Exception {
        sendImageContent(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:9080/myapp/myservice").streamCaching().process(new MyImageService());
            }
        };
    }

    public class MyImageService implements Processor {
        public void process(Exchange exchange) throws Exception {            
            exchange.getOut().setBody(new File("src/test/data/logo.jpeg"));
            exchange.getOut().setHeader("Content-Type", "image/jpeg");
        }
    }

}