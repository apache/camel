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
package org.apache.camel.component.osgi.activator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.osgi.activator.CamelRoutesActivator;
import org.apache.camel.osgi.activator.CamelRoutesActivatorConstants;
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
public class CamelOsgiActivatorIT {
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
                                        .resolve("camel-osgi-activator.jar")))
                            .build()),
                junitBundles());
    }
    
    @Test
    public void testBundleLoaded() throws Exception {
        boolean hasOsgi = false;
        boolean hasCamelCoreOsgiActivator = false;
        for (Bundle b : bc.getBundles()) {
            if ("org.apache.camel.camel-core-osgi".equals(b.getSymbolicName())) {
                hasOsgi = true;
                assertEquals("Camel Core OSGi not activated", Bundle.ACTIVE, b.getState());
            }
            
            if ("org.apache.camel.camel-osgi-activator".equals(b.getSymbolicName())) {
                hasCamelCoreOsgiActivator = true;
                assertEquals("Camel OSGi Activator not activated", Bundle.ACTIVE, b.getState());
            }
        }
        assertTrue("Camel Core OSGi bundle not found", hasOsgi);
        assertTrue("Camel OSGi Activator bundle not found", hasCamelCoreOsgiActivator);
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
    
    @Test
    public void testPreStartupLoadAndRemoved() throws Exception {
        CountDownLatch preStartLatch = new CountDownLatch(1);
        
        CountDownLatch postStartLatch = new CountDownLatch(1);
        
        CamelContext camelContext = bc.getService(bc.getServiceReference(CamelContext.class));
        
        Date originalCamelStartTime = camelContext.getStartDate(); 
        
        ServiceRegistration<RouteBuilder> testRegularServiceRegistration = bc.registerService(RouteBuilder.class,
                new RouteBuilder() {

                    @Override
                    public void configure() throws Exception {
                        from("timer:test1?fixedRate=true&period=300")
                            .description("PostStartRoute")
                            .process(exchange -> {
                                postStartLatch.countDown();
                            });
                    }
                }, null);
        
        postStartLatch.await(10, TimeUnit.SECONDS);
        
        Date regularRouteAddCamelContextStartTime = camelContext.getStartDate();
        
        assertEquals("Camel Context Should NOT be restarted when removing regular RouteBuilder", originalCamelStartTime, regularRouteAddCamelContextStartTime);
        
        assertEquals("There should be one route in the context.", 1, camelContext.getRoutes().size());
        
        assertEquals("The PostStartRoute should be first.", "PostStartRoute", camelContext.getRoutes().get(0).getDescription());

        
        Dictionary<String, String> preStartUpProperties = new Hashtable<>();
        preStartUpProperties.put(CamelRoutesActivatorConstants.PRE_START_UP_PROP_NAME, "true");
        ServiceRegistration<RouteBuilder> testPreStartupServiceRegistration = bc.registerService(RouteBuilder.class, 
                new RouteBuilder() {
                    
                    @Override
                    public void configure() throws Exception {
                        getContext().setStreamCaching(true);
                        
                        from("timer:test2?fixedRate=true&period=300")
                            .description("PreStartRoute")
                            .process(exchange -> {
                                preStartLatch.countDown();
                            });
                        
                    }
                }, preStartUpProperties);

        preStartLatch.await(10, TimeUnit.SECONDS);

        Date preStartCamelContextStartTime = camelContext.getStartDate();
        
        assertTrue("Camel Context Should be restarted when adding startup RouteBuilder", preStartCamelContextStartTime.after(originalCamelStartTime));

        assertEquals("There should be two route in the context.", 2, camelContext.getRoutes().size());
        
        assertEquals("The PreStartRoute should be first.", "PreStartRoute", camelContext.getRoutes().get(0).getDescription());

        testPreStartupServiceRegistration.unregister();

        Date preStartRemovedCamelContextStartTime = camelContext.getStartDate();
        
        assertEquals("There should be one routes in the context.", 1, camelContext.getRoutes().size());
        
        assertTrue("Camel Context Should be restarted when removing startup RouteBuilder", preStartRemovedCamelContextStartTime.after(preStartCamelContextStartTime));
        
        testRegularServiceRegistration.unregister();
        
        Date regularRouteRemovedCamelContextStartTime = camelContext.getStartDate();
        
        assertEquals("Camel Context Should NOT be restarted when removing regular RouteBuilder", preStartRemovedCamelContextStartTime, regularRouteRemovedCamelContextStartTime);

        assertEquals("There should be no routes in the context.", 0, camelContext.getRoutes().size());

    }

}
