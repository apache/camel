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
package org.apache.camel.component.restlet;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.InputRepresentation;
import org.restlet.representation.StringRepresentation;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeThat;

/**
 * @version
 */
public class RestletSetBodyTest extends RestletTestSupport {
    protected static int portNum2 =  AvailablePortFinder.getNextAvailable(4000);

    @Test
    public void testSetBody() throws Exception {
        String response = template.requestBody("restlet:http://localhost:" + portNum + "/stock/ORCL?restletMethod=get", null, String.class);
        assertEquals("110", response);
    }

    @Test
    public void testSetBodyRepresentation() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + portNum + "/images/123");
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("image/png", response.getEntity().getContentType().getValue());
            assertEquals("Get wrong available size", 256, response.getEntity().getContentLength());
            try (InputStream is = response.getEntity().getContent()) {
                byte[] buffer = new byte[256];
                assumeThat("Should read all data", is.read(buffer), equalTo(256));
                assertThat("Data should match", buffer, equalTo(getAllBytes()));
            }
        }
    }

    @Test
    public void consumerShouldReturnByteArray() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + portNum + "/music/123");
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("audio/mpeg", response.getEntity().getContentType().getValue());
            assertEquals("Content length should match returned data", 256, response.getEntity().getContentLength());
            try (InputStream is = response.getEntity().getContent()) {
                byte[] buffer = new byte[256];
                assumeThat("Should read all data", is.read(buffer), equalTo(256));
                assertThat("Binary content should match", buffer, equalTo(getAllBytes()));
            }
        }
    }

    @Test
    public void consumerShouldReturnInputStream() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + portNum + "/video/123");
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("video/mp4", response.getEntity().getContentType().getValue());
            assertTrue("Content should be streamed", response.getEntity().isChunked());
            assertEquals("Content length should be unknown", -1, response.getEntity().getContentLength());
            try (InputStream is = response.getEntity().getContent()) {
                byte[] buffer = new byte[256];
                assumeThat("Should read all data", is.read(buffer), equalTo(256));
                assertThat("Binary content should match", buffer, equalTo(getAllBytes()));
            }
        }
    }

    @Test
    public void testGzipEntity() {
        String response = template.requestBody("restlet:http://localhost:" + portNum + "/gzip/data?restletMethod=get", null, String.class);
        assertEquals("Hello World!", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/stock/{symbol}?restletMethods=get")
                    .to("http://localhost:" + portNum2 + "/test?bridgeEndpoint=true")
                    //.removeHeader("Transfer-Encoding")
                    .setBody().constant("110");

                from("jetty:http://localhost:" + portNum2 + "/test").setBody().constant("response is back");

                // create ByteArrayRepresentation for response
                from("restlet:http://localhost:" + portNum + "/images/{symbol}?restletMethods=get")
                    .setBody().constant(new InputRepresentation(
                        new ByteArrayInputStream(getAllBytes()), MediaType.IMAGE_PNG, 256));

                from("restlet:http://localhost:" + portNum + "/music/{symbol}?restletMethods=get")
                    .setHeader(Exchange.CONTENT_TYPE).constant("audio/mpeg")
                    .setBody().constant(getAllBytes());

                from("restlet:http://localhost:" + portNum + "/video/{symbol}?restletMethods=get")
                    .setHeader(Exchange.CONTENT_TYPE).constant("video/mp4")
                    .setBody().constant(new ByteArrayInputStream(getAllBytes()));

                from("restlet:http://localhost:" + portNum + "/gzip/data?restletMethods=get")
                    .setBody().constant(new EncodeRepresentation(Encoding.GZIP, new StringRepresentation("Hello World!", MediaType.TEXT_XML)));
            }
        };
    }

    private static byte[] getAllBytes() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) {
            data[i] = (byte)(Byte.MIN_VALUE + i);
        }
        return data;
    }
}
