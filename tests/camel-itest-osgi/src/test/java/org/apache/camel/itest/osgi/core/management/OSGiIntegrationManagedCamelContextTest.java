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
package org.apache.camel.itest.osgi.core.management;

import java.net.URL;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;

import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.workingDirectory;

@RunWith(PaxExam.class)
@Ignore("TODO: fix me")
public class OSGiIntegrationManagedCamelContextTest extends OSGiIntegrationTestSupport {

    protected boolean useJmx() {
        return true;
    }
    
    @Test
    public void testCamelContextName() throws Exception {
        // Wait a while to let the MBeanServer be created
        Thread.sleep(1000);
        
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();
        LOG.info("The MBeanServer is " + mbeanServer);

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=context,*"), null);
        assertEquals("There should have 2 camelcontext registed", 2, set.size());
        
        String camelContextName = context.getName();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=" + camelContextName + ",type=context,name=\"" + camelContextName + "\"");

        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        String name = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals(camelContextName, name);
    }

    private static URL getCamelContextInputStream() {
        return OSGiIntegrationManagedCamelContextTest.class.getResource("CamelContext.xml");
    }

    @Configuration
    public static Option[] configure() throws Exception {
        
        Option[] options = options(
            // install the spring dm profile            
            //profile("spring.dm").version("1.2.1"),
            
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
            
            // using the features to install the camel components             
            scanFeatures(getCamelKarafFeatureUrl(),                         
                          "camel-core", "camel-spring", "camel-test"),
            //set up the camel context bundle first             
            provision(TinyBundles.bundle().add("META-INF/spring/CamelContext.xml", getCamelContextInputStream())
                      .set(org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.itest.osgi.CamelContextTinyBundle")
                      .set(org.osgi.framework.Constants.BUNDLE_NAME, "CamelContextTinyBundle").build()),
            
            workingDirectory("target/paxrunner/"),
             
            equinox(),
            felix());
        
        return options;
    }

}
