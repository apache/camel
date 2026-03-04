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
package org.apache.camel.dsl.modeline;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.DependencyStrategy;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JBangModelineParserTest extends CamelTestSupport {

    private final List<String> deps = new ArrayList<>();

    private ModelineFactory resolveModelineFactory(CamelContext camelContext) {
        return PluginHelper.getModelineFactory(camelContext);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("myDep", new DependencyStrategy() {
            @Override
            public void onDependency(String dependency) {
                deps.add(dependency);
            }
        });
        return context;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testModelineSingleJBangDependency() throws Exception {
        context.start();

        Assertions.assertEquals(0, deps.size());

        String line = "//DEPS org.my:application:1.0";
        ModelineFactory factory = resolveModelineFactory(context);
        factory.parseModeline(ResourceHelper.fromString(null, line));

        Assertions.assertEquals(1, deps.size());
        Assertions.assertEquals("org.my:application:1.0", deps.get(0));
    }

    @Test
    public void testModelineMultiJBangDependency() throws Exception {
        context.start();
        deps.clear();

        Assertions.assertEquals(0, deps.size());

        String line = "//DEPS org.my:application:1.0 com.foo:myapp:2.1";
        ModelineFactory factory = resolveModelineFactory(context);
        factory.parseModeline(ResourceHelper.fromString(null, line));

        Assertions.assertEquals(2, deps.size());
        Assertions.assertEquals("org.my:application:1.0", deps.get(0));
        Assertions.assertEquals("com.foo:myapp:2.1", deps.get(1));
    }

}
