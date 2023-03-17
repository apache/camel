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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.spring.AbstractSpringBeanTestSupport;
import org.apache.cxf.feature.Feature;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CxfRsEndpointWithPropertiesTest extends AbstractSpringBeanTestSupport {

    @Override
    protected String[] getApplicationContextFiles() {
        return new String[] { "org/apache/camel/component/cxf/jaxrs/CxfRsEndpointWithProperties.xml" };
    }

    @Test
    public void testCxfRsBeanWithCamelPropertiesHolder() throws Exception {
        // get the camelContext from application context
        CamelContext camelContext = ctx.getBean("camel", CamelContext.class);
        CxfRsEndpoint testEndpoint = camelContext.getEndpoint("cxfrs:bean:testEndpoint", CxfRsEndpoint.class);
        assertEquals("http://localhost:9900/testEndpoint", testEndpoint.getAddress(), "Got a wrong address");

        List<Feature> features = testEndpoint.getFeatures();
        assertEquals(1, features.size(), "Single feature is expected");

        Map<String, Object> endpointProps = testEndpoint.getProperties();
        assertEquals(2, endpointProps.size(), "two endpoint properties is expected, aKey and beanId");
        assertEquals("aValue", endpointProps.get("aKey"), "Wrong property value");

        HttpGet get = new HttpGet(testEndpoint.getAddress());

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(get)) {
            assertEquals(404, response.getCode());
        }
    }

}
