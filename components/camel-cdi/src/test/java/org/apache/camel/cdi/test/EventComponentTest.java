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

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.impl.DefaultCamelContext;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(Arquillian.class)
public class EventComponentTest {

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    // We should ideally use an ExpectedException JUnit rule to assert the content of the exception
    // thrown at deployment time. Unfortunately, OpenWebBeans does not enable access to the underlying
    // cause added as deployment exception. To work-around that, we delay the start of the Camel context
    // at runtime.

    @Test
    public void createEventEndpointByUri(NotStartedCamelContext context) {
        try {
            context.start(true);
        } catch (Exception exception) {
            Throwable cause = exception.getCause().getCause();
            assertThat("Exception cause is not an UnsupportedOperationException!", cause, is(instanceOf(UnsupportedOperationException.class)));
            assertThat("Incorrect exception message!", cause.getMessage(), is(equalTo("Creating CDI event endpoint isn't supported. Use @Inject CdiEventEndpoint instead")));
            return;
        }
        fail("CDI event endpoint creation by URI should throw an exception!");
    }

    static class CdiEventComponentRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("cdi-event://Object").log("Unsupported operation!");
        }
    }

    @ApplicationScoped
    static class NotStartedCamelContext extends DefaultCamelContext {

        @Override
        public void start() {
            start(false);
        }

        void start(boolean start) {
            if (start) {
                super.start();
            }
        }
    }
}
