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
package org.apache.camel.dataformat.bindy.util;

import java.util.Set;

import org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink.Client;
import org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink.Order;
import org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink.Security;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.junit.Assert;
import org.junit.Test;

public class AnnotationModuleLoaderTest extends Assert {

    @Test
    public void testLoadModels() throws Exception {
        AnnotationModelLoader loader = new AnnotationModelLoader(new DefaultPackageScanClassResolver());
        Set<Class<?>> classes = loader.loadModels("org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink");
        assertNotNull("The findForFormattingOptions classes should not be null ", classes);
        assertEquals("There should have 3 classes", 3, classes.size());
        assertTrue(classes.contains(Client.class));
        assertTrue(classes.contains(Order.class));
        assertTrue(classes.contains(Security.class));
    }

}
