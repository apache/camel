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
package org.apache.camel.component.spring.ws;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.component.spring.ws.utils.TestUtil;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.client.core.WebServiceTemplate;

public class ConsumerEndpointMappingByBeanNameRouteTest extends CamelSpringTestSupport {

    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.stockquotes.edu/\"><symbol>GOOG</symbol></GetQuote>";

    private String expectedResponse;
    private WebServiceTemplate webServiceTemplate;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        webServiceTemplate = applicationContext.getBean("webServiceTemplate", WebServiceTemplate.class);
        expectedResponse = context.getTypeConverter().convertTo(String.class, getClass().getResourceAsStream("/stockquote-response.xml"));
    }

    @Test
    public void testBeanName() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuote));
        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        webServiceTemplate.sendSourceAndReceiveToResult(source, result);
        assertNotNull(result);
        TestUtil.assertEqualsIgnoreNewLinesSymbol(expectedResponse, sw.toString());
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/spring/ws/ConsumerEndpointMappingByBeanNameRouteTest-context.xml");
    }

}
