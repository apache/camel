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
package org.apache.camel.itest.osgi.krati;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.component.krati.KratiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class KratiRouteTest extends OSGiIntegrationTestSupport {

    protected OsgiBundleXmlApplicationContext applicationContext;

    @Inject
    protected BundleContext bundleContext;

    @Test
    public void testProducerConsumerAndIdempotent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        template.sendBodyAndHeader("direct:put", new SomeObject("1", "Test 1"), KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", new SomeObject("2", "Test 2"), KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", new SomeObject("3", "Test 3"), KratiConstants.KEY, "1");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        setThreadContextClassLoader();
        applicationContext = new OsgiBundleXmlApplicationContext(
                new String[]{"org/apache/camel/itest/osgi/krati/springKratiRouteContext.xml"});
        if (bundleContext != null) {
            applicationContext.setBundleContext(bundleContext);
            applicationContext.refresh();
        }
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                // using the features to install the camel components
                loadCamelFeatures("camel-krati"));

        return options;
    }
}