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
package org.apache.camel.itest.osgi.blueprint;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * Test cases to ensure that the Blueprint component is correctly setting the Thread's context classloader when starting
 * the routes
 *
 * @version 
 */
@RunWith(PaxExam.class)
public class CamelBlueprintTcclTest extends OSGiBlueprintTestSupport {

    private static final String BUNDLE_SYMBOLICNAME = "CamelBlueprintTcclTestBundle";

    @Test
    public void testCorrectTcclSetForRoutes() throws Exception {
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTcclTestBundle)", 10000);
        assertBundleDelegatingClassLoader(ctx.getApplicationContextClassLoader());

        ProducerTemplate template = ctx.createProducerTemplate();

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "<hello>world!</hello>");

        mock.assertIsSatisfied();

        ClassLoader tccl = mock.getExchanges().get(0).getProperty(ThreadContextClassLoaderBean.THREAD_CONTEXT_CLASS_LOADER, ClassLoader.class);
        assertNotNull("Exchange property containing TCCL should have been set", tccl);
        assertBundleDelegatingClassLoader(tccl);

        template.stop();
    }

    private void assertBundleDelegatingClassLoader(ClassLoader tccl) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // camel-blueprint does not export the BundleDelegatingClassLoader package so we need a little pinch of reflection here
        assertTrue("Expected a BundleDelegatingClassLoader instance", tccl.getClass().getName().contains("BundleDelegatingClassLoader"));
        Method getBundle = tccl.getClass().getMethod("getBundle");
        Bundle bundle = (Bundle) getBundle.invoke(tccl);

        assertEquals(BUNDLE_SYMBOLICNAME, bundle.getSymbolicName());
    }

    @Configuration
    public static Option[] configure() throws Exception {
        return combine(
            getDefaultCamelKarafOptions(),

            bundle(TinyBundles.bundle()
                    .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-tccl.xml"))
                    .add(ThreadContextClassLoaderBean.class)
                    .set(Constants.BUNDLE_SYMBOLICNAME, BUNDLE_SYMBOLICNAME)
                    .set(Constants.IMPORT_PACKAGE, "org.apache.camel")
                    .build()),

             // using the features to install the camel components
             loadCamelFeatures("camel-blueprint"));


           
    }

    /**
     * Camel {@link Processor} that injects startup thread context classloader into the exchange for testing purposes
     */
    public static final class ThreadContextClassLoaderBean implements Processor {

        public static final String THREAD_CONTEXT_CLASS_LOADER = "CamelThreadContextClassLoader";

        private final ClassLoader tccl;

        public ThreadContextClassLoaderBean() {
            tccl = Thread.currentThread().getContextClassLoader();
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.setProperty(THREAD_CONTEXT_CLASS_LOADER, tccl);
        }
    }
}
