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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * @version 
 */
@RunWith(PaxExam.class)
public class CamelBlueprintManagedNamePatternFixedTest extends OSGiBlueprintTestSupport {

    @Test
    public void testManagedNamePatternFixed() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundleFixed").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundleFixed)", 10000);

        ProducerTemplate template = ctx.createProducerTemplate();

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        template.sendBody("direct:start", "World");
        mock.assertIsSatisfied();

        MBeanServer mbeanServer = ctx.getManagementStrategy().getManagementAgent().getMBeanServer();

        assertEquals("cool", ctx.getManagementName());

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=" + ctx.getManagementName()
                + ",type=context,name=\"" + ctx.getName() + "\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(on));
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),

                bundle(TinyBundles.bundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-fixed.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundleFixed")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                // using the features to install the camel components
                loadCamelFeatures("camel-blueprint"));

        return options;
    }

}
