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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link PropertyBindingSupport.Builder} options withPlaceholder, withReference, withNesting and withLiteral, for
 * binding via reflection and via a {@link PropertyConfigurer}.
 */
public class PropertyBindingSupportFlagsTest extends ContextTestSupport {

    private static final String GREETING = Greeting.class.getName();
    private static final String SERVICE = MyService.class.getName();

    private final MyService service = new MyService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("myService", service);

        Properties placeholders = new Properties();
        placeholders.put("myKey", "Hello");
        placeholders.put("keyName", "value");
        placeholders.put("beanName", "myService");
        context.getPropertiesComponent().setInitialProperties(placeholders);
        return context;
    }

    // ------------------------------------------------------------------
    // defaults: placeholders and references are resolved
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void placeholderValuesAreResolvedWithDefaults(boolean configurer) {
        assertEquals("Hello", bindValue(builder(), configurer, "{{myKey}}"));
        assertEquals("prefix-Hello", bindValue(builder(), configurer, "prefix-{{myKey}}"));
        assertEquals(System.getProperty("java.home"), bindValue(builder(), configurer, "{{sys:java.home}}"));
    }

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void referenceValuesAreResolvedWithDefaults(boolean configurer) {
        assertEquals("Hello", bindValue(builder(), configurer, "#property:myKey"));
        assertSame(service, bindValue(builder(), configurer, "#bean:myService"));
        assertSame(service, bindValue(builder(), configurer, "#myService"));
        assertSame(service, bindValue(builder(), configurer, "#type:" + SERVICE));
        assertEquals(42, bindValue(builder(), configurer, "#valueAs(int):42"));

        Object created = bindValue(builder(), configurer, "#class:" + GREETING);
        assertInstanceOf(Greeting.class, created);

        Object factory = bindValue(builder(), configurer, "#class:" + GREETING + "#of('Hi')");
        assertInstanceOf(Greeting.class, factory);
        assertEquals("of:Hi", ((Greeting) factory).getText());

        Object ctr = bindValue(builder(), configurer, "#class:" + GREETING + "('{{myKey}}')");
        assertEquals("Hello", ((Greeting) ctr).getText());

        Target target = new Target();
        bind(builder(), configurer, target, "service", "#autowired");
        assertSame(service, target.getService());
    }

    // ------------------------------------------------------------------
    // withPlaceholder(false)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void valueIsBoundAsGivenWithPlaceholderOff(boolean configurer) {
        assertEquals("{{myKey}}", bindValue(builder().withPlaceholder(false), configurer, "{{myKey}}"));
        assertEquals("prefix-{{myKey}}", bindValue(builder().withPlaceholder(false), configurer, "prefix-{{myKey}}"));
        assertEquals("{{sys:java.home}}", bindValue(builder().withPlaceholder(false), configurer, "{{sys:java.home}}"));
    }

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void placeholderInReferenceIsNotResolvedWithPlaceholderOff(boolean configurer) {
        // placeholders in a #class: value are kept as given
        Object ctr = bindValue(builder().withPlaceholder(false), configurer, "#class:" + GREETING + "('{{myKey}}')");
        assertEquals("{{myKey}}", ((Greeting) ctr).getText());

        Object factory = bindValue(builder().withPlaceholder(false), configurer,
                "#class:" + GREETING + "#of('{{myKey}}')");
        assertEquals("of:{{myKey}}", ((Greeting) factory).getText());

        // the key of #property: is not resolved as a placeholder, so there is no such property
        Target target = new Target();
        PropertyBindingSupport.Builder b = builder().withPlaceholder(false);
        assertThrows(PropertyBindingException.class, () -> bind(b, configurer, target, "value", "#property:{{keyName}}"));

        // references are still resolved
        assertEquals("Hello", bindValue(builder().withPlaceholder(false), configurer, "#property:myKey"));
        assertSame(service, bindValue(builder().withPlaceholder(false), configurer, "#bean:myService"));
    }

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void keyIsNotResolvedWithPlaceholderOff(boolean configurer) {
        Target target = new Target();
        boolean bound = bind(builder().withPlaceholder(false), configurer, target, "{{keyName}}", "Hello");
        assertFalse(bound);
        assertNull(target.getValue());

        target = new Target();
        bound = bind(builder(), configurer, target, "{{keyName}}", "Hello");
        assertTrue(bound);
        assertEquals("Hello", target.getValue());
    }

    // ------------------------------------------------------------------
    // withReference(false)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void valueIsBoundAsGivenWithReferenceOff(boolean configurer) {
        String[] values = {
                "#property:myKey",
                "#bean:myService",
                "#myService",
                "#type:" + SERVICE,
                "#valueAs(int):42",
                "#autowired",
                "#class:" + GREETING,
                "#class:" + GREETING + "#of('Hi')",
                "#class:" + GREETING + "('Hi')" };
        for (String value : values) {
            assertEquals(value, bindValue(builder().withReference(false), configurer, value), value);
        }
    }

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void placeholderIsResolvedWithReferenceOff(boolean configurer) {
        assertEquals("Hello", bindValue(builder().withReference(false), configurer, "{{myKey}}"));
        // the placeholder is resolved, the reference it resolves to is bound as given
        assertEquals("#bean:myService",
                bindValue(builder().withReference(false), configurer, "#bean:{{beanName}}"));
    }

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void valueIsBoundAsGivenWithPlaceholderAndReferenceOff(boolean configurer) {
        PropertyBindingSupport.Builder b = builder().withPlaceholder(false).withReference(false);
        assertEquals("#property:{{keyName}}", bindValue(b, configurer, "#property:{{keyName}}"));
        b = builder().withPlaceholder(false).withReference(false);
        assertEquals("#class:" + GREETING + "('{{myKey}}')",
                bindValue(b, configurer, "#class:" + GREETING + "('{{myKey}}')"));
        b = builder().withPlaceholder(false).withReference(false);
        assertEquals("prefix-{{myKey}}", bindValue(b, configurer, "prefix-{{myKey}}"));
    }

    @Test
    public void typedOptionIsNotResolvedAsReferenceWithReferenceOff() {
        // via the configurer a value that starts with # is converted as given, which fails for a typed option
        Target target = new Target();
        PropertyBindingSupport.Builder b = builder().withReference(false);
        assertThrows(PropertyBindingException.class, () -> bind(b, true, target, "service", "#myService"));
        assertNull(target.getService());

        // via reflection there is no setter that takes the text
        Target target2 = new Target();
        PropertyBindingSupport.Builder b2 = builder().withReference(false);
        assertThrows(PropertyBindingException.class, () -> bind(b2, false, target2, "service", "#myService"));
        assertNull(target2.getService());

        // with defaults the reference is resolved
        Target target3 = new Target();
        bind(builder(), true, target3, "service", "#myService");
        assertSame(service, target3.getService());
    }

    @Test
    public void collectionValueIsBoundAsGivenWithReferenceOff() {
        Target target = new Target();
        bind(builder().withReference(false), false, target, "map[foo]", "#bean:myService");
        assertEquals("#bean:myService", target.getMap().get("foo"));

        target = new Target();
        bind(builder(), false, target, "map[foo]", "#bean:myService");
        assertSame(service, target.getMap().get("foo"));
    }

    @Test
    public void referenceOffDoesNotLeakIntoNextBinding() {
        assertEquals("#myService", bindValue(builder().withReference(false), true, "#myService"));
        assertFalse(PropertyBindingSupport.isReferenceBindingDisabled());

        // a configurer called directly (outside of a binding with references off) resolves the reference
        assertSame(service, PropertyConfigurerSupport.property(context, MyService.class, "#myService"));
        assertSame(service, bindValue(builder(), true, "#myService"));
    }

    // ------------------------------------------------------------------
    // withNesting(false)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void nestedKeyIsSkippedWithNestingOff(boolean configurer) {
        Target target = new Target();
        boolean bound = bind(builder().withNesting(false), configurer, target, "nested.text", "Hello");
        assertFalse(bound);
        assertNull(target.getNested().getText());

        target = new Target();
        bound = bind(builder(), configurer, target, "nested.text", "Hello");
        assertTrue(bound);
        assertEquals("Hello", target.getNested().getText());
    }

    // ------------------------------------------------------------------
    // withLiteral(true)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "configurer={0}")
    @ValueSource(booleans = { false, true })
    public void literalModeIsUnchanged(boolean configurer) {
        // kept as literal text
        assertEquals("{{myKey}}", bindValue(builder().withLiteral(true), configurer, "{{myKey}}"));
        Target target = new Target();
        bind(builder().withLiteral(true), configurer, target, "text", "#property:myKey");
        assertEquals("#property:myKey", target.getText());
        bind(builder().withLiteral(true), configurer, target, "text", "#class:" + GREETING);
        assertEquals("#class:" + GREETING, target.getText());
        bind(builder().withLiteral(true), configurer, target, "text", "prefix-{{myKey}}");
        assertEquals("prefix-{{myKey}}", target.getText());

        // still resolved
        assertSame(service, bindValue(builder().withLiteral(true), configurer, "#bean:myService"));
        assertSame(service, bindValue(builder().withLiteral(true), configurer, "#myService"));
        assertSame(service, bindValue(builder().withLiteral(true), configurer, "#type:" + SERVICE));
        assertEquals(42, bindValue(builder().withLiteral(true), configurer, "#valueAs(int):42"));

        target = new Target();
        bind(builder().withLiteral(true), configurer, target, "service", "#autowired");
        assertSame(service, target.getService());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static PropertyBindingSupport.Builder builder() {
        return PropertyBindingSupport.build();
    }

    private Object bindValue(PropertyBindingSupport.Builder builder, boolean configurer, String value) {
        Target target = new Target();
        assertTrue(bind(builder, configurer, target, "value", value), value);
        return target.getValue();
    }

    private boolean bind(
            PropertyBindingSupport.Builder builder, boolean configurer, Target target, String key, Object value) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(key, value);
        if (configurer) {
            builder.withConfigurer(new TargetConfigurer());
        }
        return builder.bind(context, target, props);
    }

    public static class MyService {
    }

    public static class Greeting {
        private final String text;

        public Greeting() {
            this(null);
        }

        public Greeting(String text) {
            this.text = text;
        }

        public static Greeting of(String text) {
            return new Greeting("of:" + text);
        }

        public String getText() {
            return text;
        }
    }

    public static class Nested {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class Target {
        private Object value;
        private String text;
        private MyService service;
        private Map<String, Object> map;
        private final Nested nested = new Nested();

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public MyService getService() {
            return service;
        }

        public void setService(MyService service) {
            this.service = service;
        }

        public Map<String, Object> getMap() {
            return map;
        }

        public void setMap(Map<String, Object> map) {
            this.map = map;
        }

        public Nested getNested() {
            return nested;
        }
    }

    /**
     * A hand-written configurer that converts the values like the generated configurers do.
     */
    private static class TargetConfigurer implements PropertyConfigurer, PropertyConfigurerGetter {

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if (target instanceof Target t) {
                switch (name) {
                    case "value":
                        t.setValue(PropertyConfigurerSupport.property(camelContext, Object.class, value));
                        return true;
                    case "text":
                        t.setText(PropertyConfigurerSupport.property(camelContext, String.class, value));
                        return true;
                    case "service":
                        t.setService(PropertyConfigurerSupport.property(camelContext, MyService.class, value));
                        return true;
                    default:
                        return false;
                }
            } else if (target instanceof Nested n && "text".equals(name)) {
                n.setText(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            }
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            return switch (name) {
                case "value" -> Object.class;
                case "text" -> String.class;
                case "service" -> MyService.class;
                case "nested" -> Nested.class;
                default -> null;
            };
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            if (target instanceof Target t) {
                return switch (name) {
                    case "value" -> t.getValue();
                    case "text" -> t.getText();
                    case "service" -> t.getService();
                    case "nested" -> t.getNested();
                    default -> null;
                };
            }
            return null;
        }
    }
}
