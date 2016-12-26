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
package org.apache.camel.component.cxf.jaxrs;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.spring.Main;
import org.apache.camel.test.junit4.TestSupport;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.junit.Test;

public class CxfRsAsyncRelayTest extends TestSupport {
    private static int port6 = CXFTestSupport.getPort6();
    /**
     * A sample service "interface" (technically, it is a class since we will
     * use proxy-client. That interface exposes three methods over-loading each
     * other : we are testing the appropriate one will be chosen at runtime.
     *
     */
    @WebService
    @Path("/rootpath")
    @Consumes("multipart/form-data")
    @Produces("application/xml")
    public static class UploadService {
        @WebMethod
        @POST
        @Path("/path1")
        @Consumes("multipart/form-data")
        public void upload(@Multipart(value = "content", type = "application/octet-stream") java.lang.Number content,
                           @Multipart(value = "name", type = "text/plain") String name) {
        }

        @WebMethod
        @GET
        @Path("/path2")
        @Consumes("text/plain")
        private void upload() {
        }

        @WebMethod
        @POST
        @Path("/path3")
        @Consumes("multipart/form-data")
        public void upload(@Multipart(value = "content", type = "application/octet-stream") InputStream content,
                           @Multipart(value = "name", type = "text/plain") String name) {
        }

    }

    private static final String SAMPLE_CONTENT_PATH = "/org/apache/camel/component/cxf/jaxrs/CxfRsSpringAsyncRelay.xml";
    private static final String SAMPLE_NAME = "CxfRsSpringAsyncRelay.xml";
    private static final CountDownLatch LATCH = new CountDownLatch(1);
    private static String content;
    private static String name;

    /**
     * That test builds a route chaining two cxfrs endpoints. It shows a request
     * sent to the first one will be correctly transferred and consumed by the
     * other one.
     */
    @Test
    public void testJaxrsAsyncRelayRoute() throws Exception {
        final Main main = new Main();
        try {
            main.setApplicationContextUri("org/apache/camel/component/cxf/jaxrs/CxfRsSpringAsyncRelay.xml");
            main.start();
            Thread t = new Thread(new Runnable() {
                /**
                 * Sends a request to the first endpoint in the route
                 */
                public void run() {
                    try {
                        JAXRSClientFactory.create("http://localhost:" + port6 + "/CxfRsAsyncRelayTest/rest", UploadService.class)
                            .upload(CamelRouteBuilder.class.getResourceAsStream(SAMPLE_CONTENT_PATH),
                                SAMPLE_NAME);
                    } catch (Exception e) {
                        log.warn("Error uploading to http://localhost:" + port6 + "/CxfRsAsyncRelayTest/rest", e);
                    }
                }
            });
            t.start();
            LATCH.await(10, TimeUnit.SECONDS);
            assertEquals(SAMPLE_NAME, name);
            StringWriter writer = new StringWriter();
            IOUtils.copyAndCloseInput(new InputStreamReader(CamelRouteBuilder.class
                .getResourceAsStream(SAMPLE_CONTENT_PATH)), writer);
            assertEquals(writer.toString(), content);
        } finally {
            main.stop();
        }
    }

    /**
     * Route builder to be used with
     * org/apache/camel/component/cxf/jaxrs/CxfRsSpringAsyncRelay.xml
     *
     */
    public static class CamelRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws InterruptedException {
            from("upload1").removeHeader(Exchange.CONTENT_TYPE).to("upload2Client");
            from("upload2").process(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    // once the message arrives in the second endpoint, stores
                    // the message components and warns results can be compared
                    content = exchange.getIn().getHeader("content", String.class);
                    name = exchange.getIn().getHeader("name", String.class);
                    LATCH.countDown();
                }
            });

        }
    }
}
