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
package org.apache.camel.itest.osgi.jasypt;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jasypt.JasyptPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;

import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;

@RunWith(JUnit4TestRunner.class)
public class JasyptTest extends OSGiIntegrationTestSupport {
    
    @Test
    public void testJasyptProperties() throws Exception {
        getMockEndpoint("mock:tiger").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        // create the jasypt properties parser
        JasyptPropertiesParser jasypt = new JasyptPropertiesParser();
        // and set the master password
        jasypt.setPassword("secret");

        // create the properties component
        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("classpath:org/apache/camel/itest/osgi/jasypt/myproperties.properties");
        // and use the jasypt properties parser so we can decrypt values
        pc.setPropertiesParser(jasypt);

        // add properties component to camel context
        context.addComponent("properties", pc);

        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("{{cool.result}}");
            }
        };
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            scanFeatures(getCamelKarafFeatureUrl(), "camel-jasypt"));
        
        return options;
    }
    
}