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
package org.apache.camel.itest.restlet.example;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * @version 
 */
@ContextConfiguration
public class RestletDomainServiceTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    protected ProducerTemplate template;

    @Test
    public void testAddDomain() throws Exception {
        String input = "<checkDomainRequest><id>123</id><name>www.google.com</name><username>test</username><password>test</password></checkDomainRequest>";

        String response = template.requestBodyAndHeader("restlet:http://localhost:9000/domainservice/domains?restletMethod=POST",
                input, Exchange.CONTENT_TYPE, "application/xml", String.class);

        Assert.assertNotNull(response);
        Assert.assertTrue("Should contains response", response.endsWith("<CheckDomainResponse><requestId>123</requestId><responseBody>OK</responseBody></CheckDomainResponse>"));
    }

    @Test
    public void testGetDomain() throws Exception {
        String response = template.requestBody("restlet:http://localhost:9000/domainservice/domains/123?restletMethod=GET", null, String.class);

        Assert.assertEquals("{www.google.com}", response);
    }

}
