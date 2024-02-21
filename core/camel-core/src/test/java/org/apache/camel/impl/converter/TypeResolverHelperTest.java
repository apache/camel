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
package org.apache.camel.impl.converter;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConvertible;
import org.apache.camel.support.SimpleTypeConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TypeResolverHelperTest {
    private static final Map<TypeConvertible<?, ?>, TypeConverter> registeredConverters = new HashMap<>();

    @BeforeAll
    static void setUp() {
        registeredConverters.put(new TypeConvertible<>(Source.class, Child.class), new SourceChildConverter());
    }

    @Test
    public void testTryAssignableToChild() {
        TypeConvertible<Source, Child> requestedConverter = new TypeConvertible<>(Source.class, Child.class);
        TypeConverter foundConverter = TypeResolverHelper.tryAssignableFrom(requestedConverter, registeredConverters);
        assertNotNull(foundConverter);
        Child result = foundConverter.tryConvertTo(Child.class, new Source("source"));
        assertEquals("sourceP", result.parentField);
        assertEquals("sourceC", result.childField);
    }

    // This general behaviour works in Camel 3 but stopped working in Camel 4 due to what looks like an accidental regression.
    // See https://issues.apache.org/jira/browse/CAMEL-19828
    @Test
    public void testTryAssignableToParent() {
        TypeConvertible<Source, Parent> requestedConverter = new TypeConvertible<>(Source.class, Parent.class);
        TypeConverter foundConverter = TypeResolverHelper.tryAssignableFrom(requestedConverter, registeredConverters);
        assertNotNull(foundConverter);
        Parent result = foundConverter.tryConvertTo(Parent.class, new Source("source"));
        assertEquals("sourceP", result.parentField);
    }

    private static class Child extends Parent {
        final String childField;

        private Child(String parentField, String childField) {
            super(parentField);
            this.childField = childField;
        }
    }

    private static class Parent {
        final String parentField;

        private Parent(String parentField) {
            this.parentField = parentField;
        }
    }

    private static class Source {
        final String sourceField;

        private Source(String sourceField) {
            this.sourceField = sourceField;
        }
    }

    private static class SourceChildConverter extends SimpleTypeConverter {
        private SourceChildConverter() {
            super(true, (type, exchange, value) -> {
                Source source = (Source) value;
                return new Child(source.sourceField + "P", source.sourceField + "C");
            });
        }
    }
}
