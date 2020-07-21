/*
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
package org.apache.camel.component.resteasy.test;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.resteasy.ResteasyComponent;
import org.apache.camel.component.resteasy.ResteasyHttpBinding;
import org.apache.camel.component.resteasy.test.beans.TestHttpBinding;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class ResteasySetHttpBindingTest extends CamelTestSupport {

    @Deployment
    public static Archive<?> createTestArchive() {

        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addPackage("org.apache.camel.component.resteasy")
                .addPackage("org.apache.camel.component.resteasy.servlet")
                .addClasses(TestHttpBinding.class)
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml").importRuntimeAndTestDependencies().resolve()
                        .withTransitivity().asFile())
                .addAsWebInfResource(new File("src/test/resources/webWithoutAppContext.xml"), "web.xml");
    }


    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                ResteasyComponent resteasy = new ResteasyComponent();
                ResteasyHttpBinding httpBinding = new TestHttpBinding();
                CamelContext camelContext = getContext();

                Registry registry = context.getRegistry();
                if (registry instanceof DefaultRegistry) {
                    registry = ((DefaultRegistry)registry).getFallbackRegistry();
                }

                SimpleRegistry simpleRegistry = (SimpleRegistry) registry;
                simpleRegistry.bind("binding", httpBinding);
                camelContext.addComponent("resteasy", resteasy);

                from("direct:start").to("resteasy:http://www.google"
                        + ".com?resteasyMethod=GET&restEasyHttpBindingRef=#binding");
            }
        };
    }

    @Test
    public void testSettingResteasyHttpBinding() throws Exception {
        String response = template.requestBody("direct:start", null, String.class);
        Assert.assertEquals("Test from custom HttpBinding", response);

    }
}
