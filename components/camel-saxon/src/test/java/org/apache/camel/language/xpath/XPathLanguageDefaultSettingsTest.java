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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Tests that verify the usage of default settings in the XPath language by declaring a bean called xpath in the registry
 */
public class XPathLanguageDefaultSettingsTest extends CamelSpringTestSupport {

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

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/language/xpath/XPathLanguageDefaultSettingsTest.xml");
    }

    @Test
    public void testSpringDSLXPathLanguageDefaultSettings() throws Exception {
        if (!jvmAdequate) {
            return;
        }

        MockEndpoint mockEndpointResult = getMockEndpoint("mock:testDefaultXPathSettingsResult");
        MockEndpoint mockEndpointException = getMockEndpoint("mock:testDefaultXPathSettingsResultException");

        mockEndpointResult.expectedMessageCount(1);
        mockEndpointException.expectedMessageCount(0);

        template.sendBody("seda:testDefaultXPathSettings", "<a>Hello|there|Camel</a>");

        assertMockEndpointsSatisfied();
    }

}