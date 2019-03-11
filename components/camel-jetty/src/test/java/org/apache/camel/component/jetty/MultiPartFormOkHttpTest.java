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

import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MultiPartFormOkHttpTest extends BaseJettyTest {

    private Request createMultipartRequest() throws Exception {
        MediaType mediaType = MediaType.parse("multipart/form-data; boundary=---011000010111000001101001");
        RequestBody body = RequestBody.create(mediaType, "-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"test\"\r\n\r\nsome data here\r\n-----011000010111000001101001--");
        Request request = new Request.Builder()
            .url("http://localhost:" + getPort() + "/test")
            .post(body)
            .addHeader("content-type", "multipart/form-data; boundary=---011000010111000001101001")
            .addHeader("cache-control", "no-cache")
            .addHeader("postman-token", "a9fd95b6-04b9-ea7a-687e-ff828ea00774")
            .build();
        return request;
    }

    @Test
    public void testSendMultiPartFormFromOkHttpClient() throws Exception {
        OkHttpClient client = new OkHttpClient();
        Request request = createMultipartRequest();
        Response response = client.newCall(request).execute();

        assertEquals(200, response.code());
        assertEquals("Thanks", response.body().string());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty://http://localhost:{{port}}/test").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertTrue("Should have attachment", exchange.getIn().hasAttachments());

                        InputStream is = exchange.getIn().getAttachment("test").getInputStream();
                        assertNotNull(is);
                        assertEquals("form-data; name=\"test\"", exchange.getIn().getAttachmentObject("test").getHeader("content-disposition"));
                        String data = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, is);
                        assertNotNull("Should have data", data);
                        assertEquals("some data here", data);

                        exchange.getOut().setBody("Thanks");
                    }
                });
            }
        };
    }

}
