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

import java.io.IOException;

import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.impl.DefaultClassResolver;
import org.junit.Test;

public class OsgiFactoryFinderTest extends CamelOsgiTestSupport {

    @Test
    public void testFindClass() throws Exception {
        OsgiFactoryFinder finder = new OsgiFactoryFinder(getBundleContext(), new DefaultClassResolver(), "META-INF/services/org/apache/camel/component/");
        Class<?> clazz = finder.findClass("file_test", "strategy.factory.");
        assertNotNull("We should get the file strategy factory here", clazz);
        
        try {
            clazz = finder.findClass("nofile", "strategy.factory.");
            fail("We should get exception here");
        } catch (Exception ex) {
            assertTrue("Should get NoFactoryAvailableException", ex instanceof NoFactoryAvailableException);
        }
        
        try {
            clazz = finder.findClass("file_test", "nostrategy.factory.");
            fail("We should get exception here");
        } catch (Exception ex) {
            assertTrue("Should get IOException", ex instanceof IOException);
        }
    }

}
