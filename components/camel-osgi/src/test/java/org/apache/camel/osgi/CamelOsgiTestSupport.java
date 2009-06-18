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
package org.apache.camel.osgi;

import junit.framework.TestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.osgi.framework.BundleContext;
import org.springframework.osgi.mock.MockBundle;
import org.springframework.osgi.mock.MockBundleContext;

public class CamelOsgiTestSupport extends Assert {
    private Activator testActivator;
    private MockBundleContext bundleContext = new MockBundleContext();
    private OsgiPackageScanClassResolver resolver = new OsgiPackageScanClassResolver(bundleContext);
    private MockBundle bundle = new CamelMockBundle();
    
    @Before
    public void setUp() throws Exception {        
        bundleContext.setBundle(bundle);
        testActivator = new Activator();
        testActivator.start(bundleContext);
    }
    
    @After    
    public void tearDown() throws Exception {
        testActivator.stop(bundleContext);
    }
    
    public Activator getActivator() {
        return testActivator;
    }
    
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public OsgiPackageScanClassResolver getResolver() {
        return resolver;
    }
}
