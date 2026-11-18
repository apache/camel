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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Two JARs can register a configurer with the same name (such as jackson-dataformat in camel-jackson and
 * camel-jackson3). {@link PropertyConfigurerHelper#resolvePropertyConfigurerByName(CamelContext, String, Object)} then
 * uses the configurer generated for the type of the target when the one found by name is generated for another type.
 */
public class PropertyConfigurerHelperByNameTest extends ContextTestSupport {

    private static final String NAME = "foo-dataformat";

    @Test
    public void testFallbackToConfigurerOfTarget() {
        // the configurer found by name is the one of another JAR
        PropertyConfigurer other = new OtherDataFormatConfigurer();
        resolveByName(other);

        PropertyConfigurer configurer
                = PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, NAME, new FooDataFormat());
        assertInstanceOf(FooDataFormatConfigurer.class, configurer);

        // the lookup by the type of the target maps FooDataFormat to foo-dataformat
        configurer = PropertyConfigurerHelper.resolvePropertyConfigurer(context, FooDataFormat.class);
        assertInstanceOf(FooDataFormatConfigurer.class, configurer);
    }

    @Test
    public void testConfigurerOfTargetIsKept() {
        PropertyConfigurer own = new FooDataFormatConfigurer();
        resolveByName(own);

        assertSame(own, PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, NAME, new FooDataFormat()));
    }

    @Test
    public void testTargetWithoutGeneratedConfigurerKeepsConfigurerFoundByName() {
        PropertyConfigurer other = new OtherDataFormatConfigurer();
        resolveByName(other);

        assertSame(other, PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, NAME, new BarDataFormat()));
    }

    @Test
    public void testSubclassTargetKeepsConfigurerOfParent() {
        PropertyConfigurer parent = new FooDataFormatConfigurer();
        resolveByName(parent);

        assertSame(parent,
                PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, NAME, new SubFooDataFormat()));
    }

    @Test
    public void testSubclassTargetFallsBackToConfigurerOfParent() {
        resolveByName(new OtherDataFormatConfigurer());

        PropertyConfigurer configurer
                = PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, NAME, new SubFooDataFormat());
        assertInstanceOf(FooDataFormatConfigurer.class, configurer);
    }

    @Test
    public void testConfigurerNotGeneratedIsKept() {
        PropertyConfigurer custom = new CustomConfigurer();
        resolveByName(custom);

        assertSame(custom, PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, NAME, new FooDataFormat()));
    }

    @Test
    public void testNoTargetKeepsConfigurerFoundByName() {
        PropertyConfigurer other = new OtherDataFormatConfigurer();
        resolveByName(other);

        assertSame(other, PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, NAME, null));
    }

    @Test
    public void testNoConfigurerFoundByName() {
        resolveByName(null);

        assertNull(PropertyConfigurerHelper.resolvePropertyConfigurerByName(context, NAME, new FooDataFormat()));
    }

    private void resolveByName(PropertyConfigurer configurer) {
        ConfigurerResolver resolver = (name, camelContext) -> NAME.equals(name) ? configurer : null;
        context.getCamelContextExtension().addContextPlugin(ConfigurerResolver.class, resolver);
    }

    public static class FooDataFormat {
    }

    public static class SubFooDataFormat extends FooDataFormat {
    }

    public static class BarDataFormat {
    }

    public static class OtherDataFormat {
    }

    // named after the type with the Configurer suffix, as Camel generates it
    public static class FooDataFormatConfigurer implements GeneratedPropertyConfigurer {
        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            return false;
        }
    }

    public static class OtherDataFormatConfigurer implements GeneratedPropertyConfigurer {
        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            return false;
        }
    }

    public static class CustomConfigurer implements PropertyConfigurer {
        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            return false;
        }
    }
}
