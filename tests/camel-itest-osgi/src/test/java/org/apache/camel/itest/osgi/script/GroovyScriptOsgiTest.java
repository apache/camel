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
package org.apache.camel.itest.osgi.script;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * Test camel-script for groovy expressions in OSGi
 */
@RunWith(PaxExam.class)
public class GroovyScriptOsgiTest extends OSGiIntegrationTestSupport {
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setBody().groovy("request.body * 2").to("mock:result");
            }
        };
    }

    @Test
    public void testLanguage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(6);

        template.sendBody("direct:start", 3);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // without this, "groovy.lang.*" classes will be loaded by classloader of camel-spring bundle
        context.setApplicationContextClassLoader(this.getClass().getClassLoader());
        return context;
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            loadCamelFeatures("camel-script", "camel-groovy"));
        return options;
    }
    
   
}
