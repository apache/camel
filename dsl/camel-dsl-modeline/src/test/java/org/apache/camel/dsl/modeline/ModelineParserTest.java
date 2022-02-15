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
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.DependencyStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModelineParserTest extends CamelTestSupport {

    private final List<String> deps = new ArrayList<>();

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
    public void testModelineSingleDependency() throws Exception {
        context.start();

        Assertions.assertEquals(0, deps.size());

        String line = "// camel-k: dependency=mvn:org.my:application:1.0";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals(1, deps.size());
        Assertions.assertEquals("mvn:org.my:application:1.0", deps.get(0));
    }

    @Test
    public void testModelineSingleDependencyCommentHash() throws Exception {
        context.start();

        Assertions.assertEquals(0, deps.size());

        String line = "### camel-k: dependency=mvn:org.my:application:1.0";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals(1, deps.size());
        Assertions.assertEquals("mvn:org.my:application:1.0", deps.get(0));
    }

    @Test
    public void testModelineMultiDependency() throws Exception {
        context.start();
        deps.clear();

        Assertions.assertEquals(0, deps.size());

        String line = "// camel-k: dependency=mvn:org.my:application:1.0 dependency=mvn:com.foo:myapp:2.1";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals(2, deps.size());
        Assertions.assertEquals("mvn:org.my:application:1.0", deps.get(0));
        Assertions.assertEquals("mvn:com.foo:myapp:2.1", deps.get(1));
    }

    @Test
    public void testModelineSingleProperty() throws Exception {
        context.start();

        String line = "// camel-k: property=hi=Hello";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals("Hello", context.getPropertiesComponent().parseUri("{{hi}}"));
    }

    @Test
    public void testModelineMultiProperty() throws Exception {
        context.start();

        String line = "// camel-k: property=hi=Hello property=bye=Farvel";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals("Hello", context.getPropertiesComponent().parseUri("{{hi}}"));
        Assertions.assertEquals("Farvel", context.getPropertiesComponent().parseUri("{{bye}}"));
    }

    @Test
    public void testModelineQuoteProperty() throws Exception {
        context.start();

        String line = "// camel-k: property=hi='Hello World' property=bye='Farvel Verden'";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals("Hello World", context.getPropertiesComponent().parseUri("{{hi}}"));
        Assertions.assertEquals("Farvel Verden", context.getPropertiesComponent().parseUri("{{bye}}"));
    }

    @Test
    public void testModelineMixed() throws Exception {
        context.start();
        deps.clear();

        Assertions.assertEquals(0, deps.size());

        String line = "// camel-k: dependency=mvn:org.my:application:1.0 property=hi=Hello dependency=mvn:com.foo:myapp:2.1";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals(2, deps.size());
        Assertions.assertEquals("mvn:org.my:application:1.0", deps.get(0));
        Assertions.assertEquals("mvn:com.foo:myapp:2.1", deps.get(1));

        Assertions.assertEquals("Hello", context.getPropertiesComponent().parseUri("{{hi}}"));
    }

    @Test
    public void testModelineMixedWithSpaces() throws Exception {
        context.start();
        deps.clear();

        Assertions.assertEquals(0, deps.size());

        String line
                = "//    camel-k:   dependency=mvn:org.my:application:1.0    property=hi=Hello   dependency=mvn:com.foo:myapp:2.1";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals(2, deps.size());
        Assertions.assertEquals("mvn:org.my:application:1.0", deps.get(0));
        Assertions.assertEquals("mvn:com.foo:myapp:2.1", deps.get(1));

        Assertions.assertEquals("Hello", context.getPropertiesComponent().parseUri("{{hi}}"));
    }

    @Test
    public void testModelinePropertiesFile() throws Exception {
        context.start();

        String line = "// camel-k: property=classpath:myapp.properties";
        ModelineParser parser = new ModelineParser(context);
        List<CamelContextCustomizer> customizers = parser.parse(line);
        customizers.forEach(c -> c.configure(context));

        Assertions.assertEquals("Hej", context.getPropertiesComponent().parseUri("{{hi}}"));
        Assertions.assertEquals("bar", context.getPropertiesComponent().parseUri("{{foo}}"));
    }

}
