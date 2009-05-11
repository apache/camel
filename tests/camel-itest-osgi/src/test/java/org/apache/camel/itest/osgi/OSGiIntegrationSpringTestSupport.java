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

package org.apache.camel.itest.osgi;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.OptionUtils;
import org.ops4j.pax.exam.junit.Configuration;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.knopflerfish;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.mavenConfiguration;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.logProfile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;

public abstract class OSGiIntegrationSpringTestSupport extends OSGiIntegrationTestSupport {
    
    protected OsgiBundleXmlApplicationContext applicationContext;

    protected abstract OsgiBundleXmlApplicationContext createApplicationContext();

    @Before
    public void setUp() throws Exception {
        applicationContext = createApplicationContext();
        assertNotNull("Should have created a valid spring context", applicationContext);
        super.setUp();        
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (applicationContext != null) {
            // Try to fix the test error on Hudson
            if (applicationContext.isActive()) {
                applicationContext.destroy();
            }
        }
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext.setBundleContext(bundleContext);
        applicationContext.refresh();
        String[] names = applicationContext.getBeanNamesForType(SpringCamelContext.class);
        if (names.length == 1) {
            return (SpringCamelContext)applicationContext.getBean(names[0], SpringCamelContext.class);
        } else {
            throw new RuntimeCamelException("can't create a right camel context from the application context"); 
        }
    }

}
