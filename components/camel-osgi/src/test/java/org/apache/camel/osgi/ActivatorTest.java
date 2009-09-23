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

import org.junit.Test;

public class ActivatorTest extends CamelOsgiTestSupport {    
    
    @Test
    public void testGetComponent() throws Exception {
        Class clazz = Activator.getComponent("timer");
        assertNull("Here should not find the timer component", clazz);
        
        clazz = Activator.getComponent("timer_test");
        assertNotNull("The timer_test component should not be null", clazz);
    }
    
    @Test
    public void testGetLanaguge() throws Exception {
        Class clazz = Activator.getLanguage("bean_test");
        assertNotNull("The bean_test component should not be null", clazz);
    }
    
    private boolean containsPackageName(String packageName, Activator.TypeConverterEntry[] entries) {
        for (Activator.TypeConverterEntry entry : entries) {
            for (String name : entry.converterPackages) {
                if (name.equals(packageName)) {            
                    return true;
                }
            }
        }
        return false;
    }
    
    @Test
    public void testFindTypeConverterPackageNames() throws Exception {
        Activator.TypeConverterEntry[] entries = Activator.getTypeConverterEntries();
        assertTrue("We should find some converter packages here", entries.length > 0);        
        assertTrue("Here should contains org.apache.camel.osgi.test", containsPackageName("org.apache.camel.osgi.test", entries));
        assertTrue("Here should contains org.apache.camel.osgi.test", containsPackageName("org.apache.camel.osgi.test", entries));
    }

}
