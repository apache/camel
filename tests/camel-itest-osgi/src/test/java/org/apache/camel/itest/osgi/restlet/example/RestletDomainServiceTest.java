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
package org.apache.camel.itest.osgi.restlet.example;

import org.apache.camel.Exchange;
import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * @version 
 */
@RunWith(PaxExam.class)
@Ignore("PaxExam hang on shutdown of this test")
public class RestletDomainServiceTest extends OSGiIntegrationSpringTestSupport {

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/restlet/example/camel-context.xml"});
    }

    @Test
    public void testAddDomain() throws Exception {
        String input = "<checkDomainRequest><id>123</id><name>www.google.com</name><username>test</username><password>test</password></checkDomainRequest>";

        String response = template.requestBodyAndHeader("restlet:http://localhost:9000/domainservice/domains?restletMethod=POST",
                input, Exchange.CONTENT_TYPE, "application/xml", String.class);

        log.info("Response: " + response);

        assertNotNull(response);
        assertTrue("Should contains response", response.endsWith("<CheckDomainResponse><requestId>123</requestId><responseBody>OK</responseBody></CheckDomainResponse>"));
    }

    @Test
    public void testGetDomain() throws Exception {
        String response = template.requestBody("restlet:http://localhost:9000/domainservice/domains/123?restletMethod=GET", null, String.class);
        log.info("Response: " + response);

        assertEquals("{www.google.com}", response);
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-cxf", "camel-restlet"));
        
        return options;
    }
  

}
