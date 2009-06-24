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

import java.io.IOException;
import java.util.Set;

import org.apache.camel.Converter;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.osgi.test.MyRouteBuilder;
import org.apache.camel.osgi.test.MyTypeConverter;
import org.junit.Test;
import org.osgi.framework.BundleContext;

public class OsgiPackageScanClassResolverTest extends CamelOsgiTestSupport {
    @Test
    public void testOsgiResolverFindAnnotatedTest() throws IOException {
        BundleContext  context = getActivator().getBundle().getBundleContext();
        OsgiPackageScanClassResolver resolver  = new OsgiPackageScanClassResolver(context);
             
        String[] packageNames = {"org.apache.camel.osgi.test"};
        Set<Class> classes = resolver.findAnnotated(Converter.class, packageNames);
        assertEquals("There should find a class", classes.size(), 1);
        assertTrue("Find a wrong class", classes.contains(MyTypeConverter.class));
    }
 
    @Test
    public void testOsgiResolverFindImplementationTest() {
        BundleContext  context = getActivator().getBundle().getBundleContext();
        OsgiPackageScanClassResolver resolver  = new OsgiPackageScanClassResolver(context);
        String[] packageNames = {"org.apache.camel.osgi.test"};
        Set<Class> classes = resolver.findImplementations(RoutesBuilder.class, packageNames);
        assertEquals("There should find a class", classes.size(), 1);
        assertTrue("Find a wrong class", classes.contains(MyRouteBuilder.class));
    }

}
