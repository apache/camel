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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CxfRsResponseWithHeadersTest extends CamelTestSupport {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String CXT = CXFTestSupport.getPort1() + "/CxfRsResponseWithHeadersTest";
    private static final String CXF_RS_ENDPOINT_URI
            = "cxfrs://http://localhost:" + CXT
              + "/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(CXF_RS_ENDPOINT_URI)
                        .process(e -> {
                            e.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                            e.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                            e.getMessage().setBody("Cannot find customer");
                        });
            }
        };
    }

    @Test
    public void testPutConsumer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:" + CXT + "/rest/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        put.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(put)) {
            assertEquals(404, response.getCode());
            assertEquals("Cannot find customer", EntityUtils.toString(response.getEntity()));
        }
    }

}
