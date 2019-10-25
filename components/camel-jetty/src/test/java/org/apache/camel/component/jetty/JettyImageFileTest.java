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
package org.apache.camel.component.jetty;

import java.io.File;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.MessageHelper;
import org.junit.Test;

/**
 * Unit test for exposing a http server that returns images
 */
public class JettyImageFileTest extends BaseJettyTest {

    private void sendImageContent(boolean usingGZip) throws Exception {
        Endpoint endpoint = context.getEndpoint("http://localhost:{{port}}/myapp/myservice");
        Exchange exchange = endpoint.createExchange();
        if (usingGZip) {
            exchange.getIn().setHeader(Exchange.CONTENT_ENCODING, "gzip");
        }
        template.send(endpoint, exchange);

        assertNotNull(exchange.getOut().getBody());
        assertEquals("Get a wrong content-type ", MessageHelper.getContentType(exchange.getOut()), "image/jpeg");
    }

    @Test
    public void testImageContentType() throws Exception {
        sendImageContent(false);
    }

    @Test
    public void testImageContentWithGZip() throws Exception {
        sendImageContent(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/myservice").process(new MyImageService());
            }
        };
    }

    public class MyImageService implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getOut().setBody(new File("src/test/data/logo.jpeg"));
            exchange.getOut().setHeader("Content-Type", "image/jpeg");
        }
    }

}
