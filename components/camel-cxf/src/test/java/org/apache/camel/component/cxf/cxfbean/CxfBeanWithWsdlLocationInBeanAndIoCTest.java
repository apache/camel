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
package org.apache.camel.component.cxf.cxfbean;

import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

@ContextConfiguration
public class CxfBeanWithWsdlLocationInBeanAndIoCTest extends AbstractJUnit4SpringContextTests {
    static int port = CXFTestSupport.getPort("CxfBeanWithWsdlLocationInBeanAndIoCTest.1");
    
    @Test
    public void testDoNotUseWsdlDefinedInJaxWsBeanByDefault() throws Exception {        
        HttpPost post = new HttpPost("http://localhost:" + port + "/customerservice/customers");
        post.addHeader("Accept", "text/xml");
        String body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><GetPerson xmlns=\"http://camel.apache.org/wsdl-first/types\">" 
            + "<personId>hello</personId></GetPerson></soap:Body></soap:Envelope>";
        
        StringEntity entity = new StringEntity(body, ContentType.create("text/xml", "ISO-8859-1"));
        post.setEntity(entity);
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(post);
            assertEquals(200, response.getStatusLine().getStatusCode());
            String responseBody = EntityUtils.toString(response.getEntity());
            String correct = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<GetPersonResponse xmlns=\"http://camel.apache.org/wsdl-first/types\">"
                + "<personId>hello</personId><ssn>000-000-0000</ssn><name>Bye</name></GetPersonResponse></soap:Body></soap:Envelope>";
            
            assertEquals("Get a wrong response", correct, responseBody);
        } finally {
            httpclient.close();
        }
    }
    
}
