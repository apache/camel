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
package org.apache.camel.cdi.test;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.FirstCamelContextBean;
import org.apache.camel.cdi.rule.ExpectedDeploymentException;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

@RunWith(Arquillian.class)
public class UriWithWrongContextTest {

    @ClassRule
    public static TestRule exception = ExpectedDeploymentException.none()
        .expect(RuntimeException.class)
        .expectMessage(
            // WELD-1.0, WELD-1.2, WELD-2.0 have different exception messages
            // Check only error code and injection point
            allOf(
                containsString("WELD-001408"),
                containsString("org.apache.camel.cdi.test.UriWithWrongContextRoute.inbound")));

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(FirstCamelContextBean.class, UriWithWrongContextRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void test() {
    }
}

@Named("first")
class UriWithWrongContextRoute extends RouteBuilder {

    @Inject
    @Uri(value = "direct:inbound")
    @Named("second")
    Endpoint inbound;

    @Override
    public void configure() {
        from(inbound).to("mock:outbound");
    }
}
