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

import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;
import org.osgi.service.blueprint.container.BlueprintContainer;

import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;

/**
 * @version 
 */
@RunWith(JUnit4TestRunner.class)
public class CamelBlueprint2Test extends OSGiBlueprintTestSupport {

    @Test
    public void testEndpointInjection() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle10").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle10)", 10000);
        Object producer = ctn.getComponentInstance("producer");
        assertNotNull(producer);
        assertEquals(TestProducer.class.getName(), producer.getClass().getName());
        Method mth = producer.getClass().getMethod("getTestEndpoint");
        assertNotNull(mth.invoke(producer));
    }

    @Test
    public void testRouteContext() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle11").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle11)", 10000);
        assertEquals(3, ctx.getRoutes().size());
    }

    @Test
    @Ignore("TODO: Does not work")
    public void testProxy() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle12").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle12)", 10000);
        Object proxy = ctn.getComponentInstance("myProxySender");
        assertNotNull(proxy);
        assertEquals(1, proxy.getClass().getInterfaces().length);
        assertEquals(TestProxySender.class.getName(), proxy.getClass().getInterfaces()[0].getName());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testErrorHandler() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle14").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle14)", 10000);
        assertEquals(1, ctx.getRoutes().size());
        RouteDefinition rd = ctx.getRouteDefinitions().get(0);
        assertNotNull(rd.getErrorHandlerRef());
        Object eh = ctx.getRegistry().lookup(rd.getErrorHandlerRef());
        assertEquals(DeadLetterChannelBuilder.class.getName(), eh.getClass().getName());
    }

    @Test
    public void testRouteWithNonStdComponentFromBlueprint() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle15").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle15)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle15)", 10000);
        assertEquals(1, ctx.getRoutes().size());
        assertSame(ctn.getComponentInstance("mycomp"), ctx.getComponent("mycomp"));
    }
    
    @Test
    public void testJsseUtilNamespace() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle18").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle18)", 10000);
        
        SSLContextParameters scp = (SSLContextParameters) ctn.getComponentInstance("sslContextParameters");
        
        assertEquals("TLS", scp.getSecureSocketProtocol());
        
        assertNotNull(scp.getKeyManagers());
        assertEquals("changeit", scp.getKeyManagers().getKeyPassword());
        assertNull(scp.getKeyManagers().getProvider());
        assertNotNull(scp.getKeyManagers().getKeyStore());
        assertNull(scp.getKeyManagers().getKeyStore().getType());
        
        assertNotNull(scp.getTrustManagers());
        assertNull(scp.getTrustManagers().getProvider());
        assertNotNull(scp.getTrustManagers().getKeyStore());
        assertNull(scp.getTrustManagers().getKeyStore().getType());
        
        assertNull(scp.getSecureRandom());
        
        assertNull(scp.getClientParameters());
        
        assertNull(scp.getServerParameters());
        
        assertEquals("test", scp.getCamelContext().getName());
        
        assertNotNull(scp.getCamelContext());
        assertNotNull(scp.getKeyManagers().getCamelContext());
        assertNotNull(scp.getKeyManagers().getKeyStore().getCamelContext());
        assertNotNull(scp.getTrustManagers().getCamelContext());
        assertNotNull(scp.getTrustManagers().getKeyStore().getCamelContext());
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-10.xml"))
                        .add(TestProducer.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle10")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-11.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle11")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-12.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle12")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-14.xml"))
                        .add(TestProxySender.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle14")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-15.xml"))
                        .add(TestProxySender.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle15")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),
                        
                bundle(newBundle()
                       .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-18.xml"))
                       .add(JsseUtilTester.class)
                       .add("localhost.ks", OSGiBlueprintTestSupport.class.getResourceAsStream("/org/apache/camel/itest/osgi/util/jsse/localhost.ks"))
                       .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle18")
                       .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                       .build()).noStart(),
                       
                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-blueprint", "camel-test", "camel-mail", "camel-jaxb", "camel-jms"));
                
                // for remote debugging
                // vmOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5008"));

        return options;
    }

}
