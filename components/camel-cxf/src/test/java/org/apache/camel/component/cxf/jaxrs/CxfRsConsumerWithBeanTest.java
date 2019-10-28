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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.jaxrs.testbean.ServiceUtil;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class CxfRsConsumerWithBeanTest extends CamelTestSupport {
    private static final String CXT = CXFTestSupport.getPort1() + "/CxfRsConsumerWithBeanTest";
    private static final String CXF_RS_ENDPOINT_URI = "cxfrs://http://localhost:" + CXT + "/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerServiceResource";
    private static final String CXF_RS_ENDPOINT_URI_2 = "cxfrs://http://localhost:" + CXT + "/rest2?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerServiceResource";

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("service", new ServiceUtil());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(CXF_RS_ENDPOINT_URI).to("bean://service?method=invoke(${body[0]}, ${body[1]})");
                from(CXF_RS_ENDPOINT_URI_2).bean(ServiceUtil.class, "invoke(${body[0]}, ${body[1]})");
            }
        };
    }

    @Test
    public void testPutConsumer() throws Exception {
        sendPutRequest("http://localhost:" + CXT + "/rest/customerservice/c20");
        sendPutRequest("http://localhost:" + CXT + "/rest2/customerservice/c20");
    }
    
    private void sendPutRequest(String uri) throws Exception {
        HttpPut put = new HttpPut(uri);
        StringEntity entity = new StringEntity("string");
        entity.setContentType("text/plain");
        put.setEntity(entity);
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(put);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("c20string", EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }
}