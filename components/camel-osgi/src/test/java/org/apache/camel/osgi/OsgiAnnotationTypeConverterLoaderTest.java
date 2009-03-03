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

import org.apache.camel.osgi.test.MockTypeConverterRegistry;

public class OsgiAnnotationTypeConverterLoaderTest extends CamelOsgiTestSupport {
    
    public void testLoad() throws Exception {
        OsgiAnnotationTypeConverterLoader loader = new OsgiAnnotationTypeConverterLoader(getBundleContext());
        MockTypeConverterRegistry registry = new MockTypeConverterRegistry();
        loader.load(registry);
        assertEquals("There should have a fallback converter", registry.getFallbackTypeConverters().size(), 1);
        assertEquals("There should have a coverter", registry.getTypeConverters().size(), 1);
    }

}
