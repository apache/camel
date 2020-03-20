/*
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
package org.apache.camel.impl.engine;

import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.ComponentNameResolver;
import org.junit.Test;

public class DefaultComponentNameResolverTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testDefaultComponentNameResolver() throws Exception {
        context.start();

        ComponentNameResolver resolver = context.adapt(ExtendedCamelContext.class).getComponentNameResolver();
        assertNotNull(resolver);

        Set<String> names = resolver.resolveNames(context);
        assertNotNull(names);
        assertTrue(names.size() > 20);

        assertTrue(names.contains("bean"));
        assertTrue(names.contains("direct"));
        assertTrue(names.contains("file"));
        assertTrue(names.contains("log"));
        assertTrue(names.contains("mock"));
        assertTrue(names.contains("vm"));
        assertTrue(names.contains("xslt"));

        context.stop();
    }
}
