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
package org.apache.camel.model;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24820: a bean whose type has no public no-arg constructor but a builder() or newBuilder() method (Lombok,
 * Immutables, LangChain4j, AWS SDK, protobuf, ...) is created via its builder, so it can be declared with only the type
 * and properties, without builderClass and builderMethod.
 */
public class BeanModelHelperInferredBuilderTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private BeanFactoryDefinition<?> bean(String type, Map<String, Object> properties) {
        BeanFactoryDefinition<?> def = new BeanFactoryDefinition<>();
        def.setName("myBean");
        def.setType(type);
        def.setProperties(properties);
        return def;
    }

    private static Map<String, Object> props(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    public void testBuilderInferred() throws Exception {
        // the same shape as dev.langchain4j.model.ollama.OllamaChatModel: builder() and a nested builder with build()
        Object out = BeanModelHelper.newInstance(
                bean(ChatModel.class.getName(), props("baseUrl", "http://localhost:11434", "modelName", "qwen2.5",
                        "temperature", "0.0", "timeout", "120s")),
                context);

        ChatModel model = assertInstanceOf(ChatModel.class, out);
        assertEquals("http://localhost:11434", model.baseUrl);
        assertEquals("qwen2.5", model.modelName);
        assertEquals(0.0, model.temperature);
        assertEquals(Duration.ofSeconds(120), model.timeout);
    }

    @Test
    public void testBuilderInferredWithClassPrefix() throws Exception {
        Object out = BeanModelHelper.newInstance(
                bean("#class:" + ChatModel.class.getName(), props("modelName", "llama3")), context);
        assertEquals("llama3", assertInstanceOf(ChatModel.class, out).modelName);
    }

    @Test
    public void testBuilderInferredWithoutProperties() throws Exception {
        Object out = BeanModelHelper.newInstance(bean(ChatModel.class.getName(), null), context);
        ChatModel model = assertInstanceOf(ChatModel.class, out);
        assertNull(model.modelName);
        assertEquals(Duration.ofSeconds(60), model.timeout, "the builder default");
    }

    @Test
    public void testNewBuilderAndNoBuildMethodInferred() throws Exception {
        // newBuilder() as the JDK HttpClient and protobuf, and create() as the only method that returns the type
        Object out = BeanModelHelper.newInstance(
                bean(Channel.class.getName(), props("host", "localhost", "port", "8080")), context);
        Channel channel = assertInstanceOf(Channel.class, out);
        assertEquals("localhost:8080", channel.address);
    }

    @Test
    public void testBuilderMethodOverridesInferred() throws Exception {
        // the builder is still inferred, only the method that creates the bean is given
        BeanFactoryDefinition<?> def = bean(Ambiguous.class.getName(), props("name", "x"));
        def.setBuilderMethod("large");

        Ambiguous out = assertInstanceOf(Ambiguous.class, BeanModelHelper.newInstance(def, context));
        assertEquals("large x", out.size);
    }

    @Test
    public void testPropertiesTheBuilderDoesNotTakeAreSetOnTheBean() throws Exception {
        Object out = BeanModelHelper.newInstance(
                bean(ChatModel.class.getName(), props("modelName", "qwen2.5", "label", "support")), context);
        ChatModel model = assertInstanceOf(ChatModel.class, out);
        assertEquals("qwen2.5", model.modelName);
        assertEquals("support", model.label, "label has a setter on the bean, not on the builder");
    }

    @Test
    public void testUnknownPropertyNamesWhatTheBuilderAccepts() {
        Exception e = assertThrows(Exception.class, () -> BeanModelHelper.newInstance(
                bean(ChatModel.class.getName(), props("modelName", "qwen2.5", "unknown", "x")), context));
        String msg = e.getMessage();
        assertTrue(msg.contains("unknown=x"), msg);
        assertTrue(msg.contains("The bean is created through its builder " + ChatModel.ChatModelBuilder.class.getName()
                                + ", which accepts: baseUrl, modelName, temperature, timeout"),
                msg);
        assertTrue(msg.contains("the created bean accepts: label"), msg);
    }

    @Test
    public void testNoConstructorAndNoBuilderNamesTheAlternatives() {
        Exception e = assertThrows(Exception.class,
                () -> BeanModelHelper.newInstance(bean(Factories.class.getName(), props("name", "x")), context));
        String msg = e.getMessage();
        assertTrue(msg.startsWith("Cannot create bean of class " + Factories.class.getName()
                                  + " has no public no-arg constructor and no builder() or newBuilder() method"),
                msg);
        assertTrue(msg.contains("constructor arguments (constructors: in YAML, or #class:" + Factories.class.getName()
                                + "('value', ...) in properties) for Factories(String, int)"),
                msg);
        assertTrue(msg.contains("with factoryMethod (and constructors: for its arguments) for the static of(String), "
                                + "ofDefault()"),
                msg);
        assertTrue(msg.contains("or with a builder class of its own (builderClass and builderMethod)"), msg);

        assertNull(PropertyBindingSupport.noPublicConstructorHint(ChatModel.class), "a builder needs no hint");
        assertNull(PropertyBindingSupport.noPublicConstructorHint(Plain.class), "a constructor needs no hint");
    }

    @Test
    public void testPublicConstructorWins() throws Exception {
        // a class that has both a constructor and a builder keeps being created with the constructor
        Object out = BeanModelHelper.newInstance(bean(Plain.class.getName(), props("name", "Camel")), context);
        Plain plain = assertInstanceOf(Plain.class, out);
        assertEquals("Camel", plain.name);
        assertFalse(plain.built);
    }

    @Test
    public void testFactoryMethodIsNotInferred() throws Exception {
        BeanFactoryDefinition<?> def = bean(ChatModel.class.getName(), null);
        def.setFactoryMethod("demo");

        ChatModel model = assertInstanceOf(ChatModel.class, BeanModelHelper.newInstance(def, context));
        assertEquals("demo", model.modelName);
    }

    @Test
    public void testAmbiguousBuilderMethodFails() {
        Exception e = assertThrows(Exception.class,
                () -> BeanModelHelper.newInstance(bean(Ambiguous.class.getName(), props("name", "x")), context));
        assertTrue(e.getMessage().contains("Specify the method to use with builderMethod"), e.getMessage());
        assertTrue(e.getMessage().contains("small") && e.getMessage().contains("large"), e.getMessage());
    }

    @Test
    public void testNoBuilderStillFails() {
        Exception e = assertThrows(Exception.class,
                () -> BeanModelHelper.newInstance(bean(NoBuilder.class.getName(), null), context));
        String msg = e.getMessage();
        assertTrue(msg.contains(NoBuilder.class.getName() + " has no public no-arg constructor"), msg);
        assertTrue(msg.contains("Create it with a builder class of its own (builderClass and builderMethod)"), msg);
    }

    @Test
    public void testInheritedFluentSettersOfASelfTypedBaseBuilder() {
        // as the LangChain4j Gemini builder: the setters are declared on a generic base builder and return B
        assertEquals(java.util.List.of("apiKey", "modelName", "region"),
                PropertyBindingSupport.builderPropertyNames(Regional.RegionalBuilder.class));
    }

    @Test
    public void testResolveBeanViaClassUsesBuilder() throws Exception {
        // #class: without properties (as camel.beans.x = #class:... with no further keys) builds with the defaults
        Object out = PropertyBindingSupport.resolveBean(context, "#class:" + ChatModel.class.getName());
        assertEquals(Duration.ofSeconds(60), assertInstanceOf(ChatModel.class, out).timeout);

        assertTrue(PropertyBindingSupport.isBuilderOnly(ChatModel.class));
        assertTrue(PropertyBindingSupport.isBuilderOnly(Channel.class));
        assertFalse(PropertyBindingSupport.isBuilderOnly(Plain.class));
        assertFalse(PropertyBindingSupport.isBuilderOnly(NoBuilder.class));
        assertFalse(PropertyBindingSupport.isBuilderOnly(String.class));
    }

    // the test classes: builders in the shapes found in the wild

    /**
     * Like a LangChain4j model: private constructor, static builder(), nested builder with fluent setters and build()
     */
    public static class ChatModel {
        final String baseUrl;
        final String modelName;
        final Double temperature;
        final Duration timeout;
        String label;

        private ChatModel(ChatModelBuilder b) {
            this.baseUrl = b.baseUrl;
            this.modelName = b.modelName;
            this.temperature = b.temperature;
            this.timeout = b.timeout;
        }

        public static ChatModelBuilder builder() {
            return new ChatModelBuilder();
        }

        public static ChatModel demo() {
            return builder().modelName("demo").build();
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public static class ChatModelBuilder {
            private String baseUrl;
            private String modelName;
            private Double temperature;
            private Duration timeout = Duration.ofSeconds(60);

            public ChatModelBuilder baseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
                return this;
            }

            public ChatModelBuilder modelName(String modelName) {
                this.modelName = modelName;
                return this;
            }

            public ChatModelBuilder temperature(Double temperature) {
                this.temperature = temperature;
                return this;
            }

            public ChatModelBuilder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            public ChatModel build() {
                return new ChatModel(this);
            }
        }
    }

    /** Like the JDK HttpClient or protobuf: newBuilder(), and no build() but a single method returning the type */
    public static final class Channel {
        final String address;
        final boolean secure;

        private Channel(String address, boolean secure) {
            this.address = address;
            this.secure = secure;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public static final class Builder {
            private String host = "localhost";
            private int port = 80;
            private boolean secure;

            public Builder host(String host) {
                this.host = host;
                return this;
            }

            public Builder port(int port) {
                this.port = port;
                return this;
            }

            // returns the builder, so not a candidate
            public Builder secure() {
                this.secure = true;
                return this;
            }

            // returns something else, so not a candidate
            public String describe() {
                return host;
            }

            public Channel create() {
                return new Channel(host + ":" + port, secure);
            }
        }
    }

    /** Has a public no-arg constructor as well as a builder: created with the constructor as before */
    public static class Plain {
        String name;
        boolean built;

        public Plain() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public void setName(String name) {
            this.name = name;
        }

        public static class Builder {
            public Builder name(String name) {
                return this;
            }

            public Plain build() {
                Plain p = new Plain();
                p.built = true;
                return p;
            }
        }
    }

    /** No build() and two methods returning the type: the builder method must be given */
    public static final class Ambiguous {
        final String size;

        private Ambiguous(String size) {
            this.size = size;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String name;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Ambiguous small() {
                return new Ambiguous("small " + name);
            }

            public Ambiguous large() {
                return new Ambiguous("large " + name);
            }
        }
    }

    /** A builder whose setters are inherited from a self-typed base builder (B extends BaseBuilder<B>) */
    public static final class Regional {
        private Regional() {
        }

        public static RegionalBuilder builder() {
            return new RegionalBuilder();
        }

        public abstract static class BaseBuilder<B extends BaseBuilder<B>> {
            @SuppressWarnings("unchecked")
            public B apiKey(String apiKey) {
                return (B) this;
            }

            @SuppressWarnings("unchecked")
            public B modelName(String modelName) {
                return (B) this;
            }
        }

        public static final class RegionalBuilder extends BaseBuilder<RegionalBuilder> {
            public RegionalBuilder region(String region) {
                return this;
            }

            /** a single-arg method that returns Object (as Groovy's propertyMissing) is not a fluent setter */
            public Object lookup(String key) {
                return null;
            }

            public Regional build() {
                return new Regional();
            }
        }
    }

    /** No public constructor and no builder: fails as before */
    public static final class NoBuilder {
        private NoBuilder() {
        }
    }

    /** No no-arg constructor and no builder, but a constructor with arguments and static factory methods */
    public static final class Factories {
        public Factories(String name, int port) {
        }

        public static Factories of(String name) {
            return new Factories(name, 80);
        }

        public static Factories ofDefault() {
            return of("default");
        }

        // returns another type, so not a factory
        public static String describe() {
            return "factories";
        }
    }
}
