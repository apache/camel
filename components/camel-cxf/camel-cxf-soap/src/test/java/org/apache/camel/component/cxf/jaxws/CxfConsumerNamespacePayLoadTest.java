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
package org.apache.camel.component.cxf.jaxws;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfConsumerNamespacePayLoadTest extends CxfConsumerPayloadTest {
    private static final String ECHO_RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                                + "<soap:Body><ns1:echoResponse xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\">"
                                                + "<return xmlns=\"http://jaxws.cxf.component.camel.apache.org/\">echo Hello World!</return>"
                                                + "</ns1:echoResponse></soap:Body></soap:Envelope>";

    private static final String ECHO_REQUEST = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                                               + "xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                                               + "xmlns:ns2=\"http://jaxws.cxf.component.camel.apache.org/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><soap:Body>"
                                               + "<ns1:echo><ns2:arg0 xsi:type=\"xsd:string\">Hello World!</ns2:arg0></ns1:echo></soap:Body></soap:Envelope>";

    @Override
    protected void checkRequest(String expect, String request) {
        if (expect.equals(ECHO_REQUEST)) {
            // just check the namespace of xsd
            assertTrue(request.indexOf("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"") > 0, "Expect to find the namespace");
        }
    }

    @Override
    @Test
    public void testInvokingServiceFromClient() throws Exception {
        // just send a request which has all the namespace in the soap header
        HttpPost post = new HttpPost(simpleEndpointAddress);
        post.addHeader("Accept", "text/xml");

        StringEntity entity = new StringEntity(ECHO_REQUEST, ContentType.create("text/xml", "ISO-8859-1"));
        post.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getCode());
            String responseBody = EntityUtils.toString(response.getEntity());

            assertEquals(ECHO_RESPONSE, responseBody, "Get a wrong response");
        }

    }

}
