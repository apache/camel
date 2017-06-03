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
package org.apache.camel.language.xpath;

import javax.xml.xpath.XPathFactory;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class XPathLanguageTest extends CamelSpringTestSupport {

    private static final String KEY = XPathFactory.DEFAULT_PROPERTY_NAME + ":" + "http://java.sun.com/jaxp/xpath/dom";
    private boolean jvmAdequate = true;
    private String oldPropertyValue;

    @Override
    public void setUp() throws Exception {
        if (!isJavaVendor("ibm")) {
            // Force using the JAXP default implementation, because having Saxon in the classpath will automatically make JAXP use it
            // because of Service Provider discovery (this does not happen in OSGi because the META-INF/services package is not exported
            oldPropertyValue = System.setProperty(KEY, "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl");
        } else {
            jvmAdequate = false;
        }
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        if (oldPropertyValue != null) {
            System.setProperty(KEY, oldPropertyValue);
        } else {
            System.clearProperty(KEY);
        }
        super.tearDown();
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/language/xpath/XPathLanguageTest.xml");
    }

    @Test
    public void testSpringDSLXPathSaxonFlag() throws Exception {
        if (!jvmAdequate) {
            return;
        }

        MockEndpoint mockEndpoint = getMockEndpoint("mock:testSaxonWithFlagResult");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:testSaxonWithFlag", "<a>Hello|there|Camel</a>");

        assertMockEndpointsSatisfied();
        Exchange received = mockEndpoint.getExchanges().get(0);
        Object body = received.getIn().getBody();
        assertEquals("Hello", body);
    }

    @Test
    public void testSpringDSLXPathFactory() throws Exception {
        if (!jvmAdequate) {
            return;
        }

        MockEndpoint mockEndpoint = getMockEndpoint("mock:testSaxonWithFactoryResult");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:testSaxonWithFactory", "<a>Hello|there|Camel</a>");

        assertMockEndpointsSatisfied();
        Exchange received = mockEndpoint.getExchanges().get(0);
        Object body = received.getIn().getBody();
        assertEquals("Hello", body);
    }

    @Ignore("See http://www.saxonica.com/documentation/index.html#!xpath-api/jaxp-xpath/factory")
    @Test
    public void testSpringDSLXPathObjectModel() throws Exception {
        if (!jvmAdequate) {
            return;
        }

        MockEndpoint mockEndpoint = getMockEndpoint("mock:testSaxonWithObjectModelResult");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:testSaxonWithObjectModel", "<a>Hello|there|Camel</a>");

        assertMockEndpointsSatisfied();
        Exchange received = mockEndpoint.getExchanges().get(0);
        Object body = received.getIn().getBody();
        assertEquals("Hello", body);
    }

    @Test
    public void testSpringDSLXPathSaxonFlagPredicate() throws Exception {
        if (!jvmAdequate) {
            return;
        }

        MockEndpoint mockEndpoint = getMockEndpoint("mock:testSaxonWithFlagResultPredicate");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:testSaxonWithFlagPredicate", "<a>Hello|there|Camel</a>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSpringDSLXPathFactoryPredicate() throws Exception {
        if (!jvmAdequate) {
            return;
        }

        MockEndpoint mockEndpoint = getMockEndpoint("mock:testSaxonWithFactoryResultPredicate");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:testSaxonWithFactoryPredicate", "<a>Hello|there|Camel</a>");

        assertMockEndpointsSatisfied();
    }

    @Ignore("See http://www.saxonica.com/documentation/index.html#!xpath-api/jaxp-xpath/factory")
    @Test
    public void testSpringDSLXPathObjectModelPredicate() throws Exception {
        if (!jvmAdequate) {
            return;
        }

        MockEndpoint mockEndpoint = getMockEndpoint("mock:testSaxonWithObjectModelResultPredicate");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:testSaxonWithObjectModelPredicate", "<a>Hello|there|Camel</a>");

        assertMockEndpointsSatisfied();
    }

}