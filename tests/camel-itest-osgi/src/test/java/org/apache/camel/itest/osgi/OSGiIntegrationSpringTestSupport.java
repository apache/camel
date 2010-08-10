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
import org.junit.After;
import org.junit.Before;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

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
    public void tearDown() {
        try {
            super.tearDown();
            if (applicationContext != null) {                
                if (applicationContext.isActive()) {
                    applicationContext.destroy();
                }
                applicationContext = null;
            }
        } catch (Exception exception) {
            // Don't throw the exception in the tearDown method            
        }
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext.setBundleContext(bundleContext);
        applicationContext.refresh();
        String[] names = applicationContext.getBeanNamesForType(SpringCamelContext.class);
        if (names.length == 1) {
            return applicationContext.getBean(names[0], SpringCamelContext.class);
        } else {
            throw new RuntimeCamelException("can't create a right camel context from the application context"); 
        }
    }

}
