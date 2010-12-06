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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

public class OsgiComponentResolverTest extends CamelOsgiTestSupport {
    
    @Test
    public void testOsgiResolverFindLanguageTest() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        OsgiComponentResolver resolver = new OsgiComponentResolver(getBundleContext());
        Component component = resolver.resolveComponent("file_test", camelContext);
        assertNotNull("We should find file_test component", component);
        assertTrue("We should get the file component here", component instanceof FileComponent);
    }

}
