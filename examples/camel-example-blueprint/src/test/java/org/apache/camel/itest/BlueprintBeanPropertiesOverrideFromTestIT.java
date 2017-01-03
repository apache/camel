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
package org.apache.camel.itest;

import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.Hello;
import org.apache.camel.example.HelloBean;
import org.apache.camel.test.karaf.CamelKarafTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.util.Filter;
import org.ops4j.pax.swissbox.tinybundles.dp.Constants;
import org.osgi.service.cm.ConfigurationAdmin;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;

@RunWith(PaxExam.class)
public class BlueprintBeanPropertiesOverrideFromTestIT extends CamelKarafTestSupport {
    @Inject
    ConfigurationAdmin caService;

    @Inject
    @Filter(value = "(camel.context.name=blueprint-bean-context)", timeout = 30000)
    CamelContext testContext;

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Override
    protected void doPreSetup() throws Exception {
        org.osgi.service.cm.Configuration cmConfig = caService.getConfiguration("HelloBean");

        Dictionary<String, Object> props = new Hashtable<String, Object>();

        props.put("greeting", "Hi from Camel - test property value");

        cmConfig.update(props);

        super.doPreSetup();
    }

    /*
        */
    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer://testing-context")
                        .log("********** Received Event **********");
            }
        };
    }

    @Configuration
    public Option[] configure() throws Exception {
        return combine(
                configure("camel-core-osgi", "camel-blueprint", "camel-test"),
                mavenBundle().groupId("org.apache.camel").artifactId("camel-core-osgi").versionAsInProject(),
                streamBundle(
                        bundle()
                                .add(Hello.class)
                                .add(HelloBean.class)
                                .add("OSGI-INF/blueprint/blueprint-camel-context.xml", new URL("file:src/main/resources/OSGI-INF/blueprint/blueprint-camel-context.xml"))
                                .set(Constants.BUNDLE_MANIFESTVERSION, "2")
                                .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.blueprint-test")
                                .set(Constants.BUNDLE_VERSION, "0.0.0")
                                .build()
                )
        );
    }

    /*
    */
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = testContext;

        return context;
    }

    @Test
    public void testReplacePropertiesFromTest() throws Exception {
        // the route is timer based, so every 2 seconds a message is sent
        MockEndpoint result = getMockEndpoint("mock://result");
        result.expectedMinimumMessageCount(1);
        result.expectedBodyReceived().body().contains("test property value");

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

}
