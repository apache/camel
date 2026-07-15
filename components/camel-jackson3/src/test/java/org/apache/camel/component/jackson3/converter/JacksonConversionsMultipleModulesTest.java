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
package org.apache.camel.component.jackson3.converter;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson3.JacksonConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonConversionsMultipleModulesTest extends CamelTestSupport {

    @Test
    public void shouldRegisterAllConfiguredModules() {
        context.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        context.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");
        context.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_MODULE_CLASS_NAMES,
                FooModule.class.getName() + "," + BarModule.class.getName());

        String foo = (String) template.requestBody("direct:test", new Foo());
        String bar = (String) template.requestBody("direct:test", new Bar());

        assertEquals("\"foo\"", foo);
        assertEquals("\"bar\"", bar);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test").convertBodyTo(String.class);
            }
        };
    }

    public static class Foo {
    }

    public static class Bar {
    }

    public static class FooModule extends SimpleModule {
        public FooModule() {
            super("foo-module");
            addSerializer(Foo.class, new ValueSerializer<Foo>() {
                @Override
                public void serialize(Foo value, JsonGenerator gen, SerializationContext ctxt) {
                    gen.writeString("foo");
                }
            });
        }
    }

    public static class BarModule extends SimpleModule {
        public BarModule() {
            super("bar-module");
            addSerializer(Bar.class, new ValueSerializer<Bar>() {
                @Override
                public void serialize(Bar value, JsonGenerator gen, SerializationContext ctxt) {
                    gen.writeString("bar");
                }
            });
        }
    }

}
