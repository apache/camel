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

import org.apache.camel.util.jsse.SSLContextParameters;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;
import org.osgi.service.blueprint.container.BlueprintContainer;

import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * @version 
 */
@RunWith(PaxExam.class)
public class CamelBlueprint8Test extends OSGiBlueprintTestSupport {

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

                bundle(TinyBundles.bundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-10.xml"))
                        .add(TestProducer.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle10")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),


                bundle(TinyBundles.bundle()
                       .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-18.xml"))
                       .add(JsseUtilTester.class)
                       .add("localhost.ks", OSGiBlueprintTestSupport.class.getResourceAsStream("/org/apache/camel/itest/osgi/util/jsse/localhost.ks"))
                       .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle18")
                       .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                       .build()).noStart(),

                // using the features to install the camel components
                loadCamelFeatures("camel-blueprint"));
                
                // for remote debugging
                // vmOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5008"));

        return options;
    }

}
