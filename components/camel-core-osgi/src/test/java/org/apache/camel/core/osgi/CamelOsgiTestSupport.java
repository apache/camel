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
package org.apache.camel.core.osgi;

import org.apache.camel.spi.ClassResolver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.osgi.framework.BundleContext;
import org.springframework.osgi.mock.MockBundle;
import org.springframework.osgi.mock.MockBundleContext;

public class CamelOsgiTestSupport extends Assert {
    private MockBundle bundle = new CamelMockBundle();
    private MockBundleContext bundleContext = new CamelMockBundleContext(bundle);
    private OsgiPackageScanClassResolver packageScanClassResolver = new OsgiPackageScanClassResolver(bundleContext);
    private ClassResolver classResolver = new OsgiClassResolver(null, bundleContext);

    @Before
    public void setUp() throws Exception {        
        bundleContext.setBundle(bundle);
    }
    
    @After    
    public void tearDown() throws Exception {
    }
    
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public OsgiPackageScanClassResolver getPackageScanClassResolver() {
        return packageScanClassResolver;
    }
    
    public ClassResolver getClassResolver() {
        return classResolver;
    }
}
