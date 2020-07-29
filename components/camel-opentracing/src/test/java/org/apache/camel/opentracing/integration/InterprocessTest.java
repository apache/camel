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
package org.apache.camel.opentracing.integration;


import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import io.opentracing.contrib.grizzly.ahc.TracingRequestFilter;
import io.opentracing.tag.Tags;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentracing.CamelOpenTracingTestSupport;
import org.apache.camel.opentracing.SpanTestData;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;


public class InterprocessTest extends CamelOpenTracingTestSupport {
    private static final String URL = "http://localhost:" + AvailablePortFinder.getNextAvailable() + "/test";
    private static final String URI = "jetty:" + URL;

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("jetty:start server").setUri(URI).setOperation("GET")
                    .setKind("server")
                    .addTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                    .addTag(Tags.COMPONENT.getKey(), "camel-jetty")
                    .addTag(Tags.HTTP_URL.getKey(), URL)
                    .addTag(Tags.HTTP_METHOD.getKey(), "GET")
                    // jetty does not set the http response code header
                    // this is where instrumentation has value
                    // .addTag(Tags.HTTP_STATUS.getKey(), "200")
                    .setParentId(1),
            new SpanTestData().setLabel("ahc: client").setUri(null).setOperation("HTTP::GET")
                    .setKind("client")
                    .addTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .addTag(Tags.COMPONENT.getKey(), "java-grizzly-ahc")
                    .addTag(Tags.HTTP_URL.getKey(), URL)
                    .addTag(Tags.HTTP_METHOD.getKey(), "GET")
                    .addTag(Tags.HTTP_STATUS.getKey(), "200")
    };

    public InterprocessTest() {
        super(testdata);
    }

    @Test
    public void testBridgeEndpoint() throws Exception {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .addRequestFilter(new TracingRequestFilter(getTracer()))
                .build();

        try (AsyncHttpClient client = new AsyncHttpClient(config)) {
            client.prepareGet(URL).execute().get();
        }

        verify();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // get the request URL and copy it to the request body
                        String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                        exchange.getMessage().setBody(uri);
                    }
                };

                from(URI).process(serviceProc);
            }
        };
    }
}
