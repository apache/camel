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
package org.apache.camel.itest.karaf.main;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BundleIT {
    @Inject
    private BundleContext bc;

    @Configuration
    public Option[] configuration() throws IOException, URISyntaxException, ClassNotFoundException {
        return options(
                PaxExamOptions.KARAF.option(),
                PaxExamOptions.CAMEL_CORE_OSGI.option(),
                streamBundle(
                    TinyBundles.bundle()
                        .read(
                            Files.newInputStream(
                                Paths.get("target/test-bundles")
                                    .resolve("camel-core-osgi-activator.jar")))
                        .build()),
                junitBundles());
    }
    
    @Test
    public void testBundleLoaded() throws Exception {
        boolean hasCore = false;
        boolean hasOsgi = false;
        boolean hasCamelCoreOsgiActivator = false;
        for (Bundle b : bc.getBundles()) {
            if ("org.apache.camel.camel-core".equals(b.getSymbolicName())) {
                hasCore = true;
                assertEquals("Camel Core not activated", Bundle.ACTIVE, b.getState());
            }
            if ("org.apache.camel.camel-core-osgi".equals(b.getSymbolicName())) {
                hasOsgi = true;
                assertEquals("Camel Core OSGi not activated", Bundle.ACTIVE, b.getState());
            }
            
            if ("org.apache.camel.camel-core-osgi-activator".equals(b.getSymbolicName())) {
                hasCamelCoreOsgiActivator = true;
                assertEquals("Camel Core OSGi Activator not activated", Bundle.ACTIVE, b.getState());
            }
        }
        assertTrue("Camel Core bundle not found", hasCore);
        assertTrue("Camel Core OSGi bundle not found", hasOsgi);
        assertTrue("Camel Core OSGi Activator bundle not found", hasCamelCoreOsgiActivator);
    }

    @Test
    public void testRouteLoadAndRemoved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ServiceRegistration<RouteBuilder> testServiceRegistration = bc.registerService(RouteBuilder.class,
                new RouteBuilder() {

                    @Override
                    public void configure() throws Exception {
                        from("timer:test?fixedRate=true&period=300").process(exchange -> {
                            latch.countDown();
                        });
                    }
                }, null);

        latch.await(10, TimeUnit.SECONDS);

        CamelContext camelContext = bc.getService(bc.getServiceReference(CamelContext.class));

        assertEquals("There should be one route in the context.", 1, camelContext.getRoutes().size());

        testServiceRegistration.unregister();

        assertEquals("There should be no routes in the context.", 0, camelContext.getRoutes().size());

    }

}
