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
package org.apache.camel.swagger;

import java.lang.invoke.MethodType;
import java.util.List;

import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.SerializableParameter;
import io.swagger.models.properties.Property;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;

import static java.lang.invoke.MethodHandles.publicLookup;

public final class ParameterAssert extends ObjectAssert<Parameter> {

    private ParameterAssert(final Parameter actual) {
        super(actual);
    }

    public <T> ParameterAssert hasArrayEnumSpecifiedWith(@SuppressWarnings("unchecked") final T... values) {
        isSerializable();

        final SerializableParameter serializableParameter = (SerializableParameter) actual;
        final Property items = serializableParameter.getItems();
        final List<T> arrayItems = fetchEnums(items);
        Assertions.assertThat(arrayItems).containsOnly(values);

        return this;
    }

    public ParameterAssert hasEnumSpecifiedWith(final String... values) {
        isSerializable();

        final SerializableParameter serializableParameter = (SerializableParameter) actual;
        final List<String> actualEnum = serializableParameter.getEnum();

        Assertions.assertThat(actualEnum).containsOnly(values);

        return this;
    }

    public ParameterAssert hasName(final String name) {
        final String actualName = actual.getName();
        Assertions.assertThat(actualName).as("Parameter name should equal %s, but it's %s", name, actualName);

        return this;
    }

    public ParameterAssert isGivenIn(final String in) {
        final String actualIn = actual.getIn();
        Assertions.assertThat(actualIn).as("Parameter should be specified in %s, but it's in %s", in, actualIn)
            .isEqualTo(in);

        return this;
    }

    public ParameterAssert isOfArrayType(final String type) {
        isSerializable();

        final SerializableParameter serializableParameter = (SerializableParameter) actual;
        final Property items = serializableParameter.getItems();
        Assertions.assertThat(items).isNotNull();
        final String actualArrayType = items.getType();
        Assertions.assertThat(actualArrayType).as("Parameter array should be of %s type, but it's of %s", type,
            actualArrayType);

        return this;
    }

    public ParameterAssert isOfType(final String type) {
        isSerializable();

        final SerializableParameter serializableParameter = (SerializableParameter) actual;
        final String actualType = serializableParameter.getType();
        Assertions.assertThat(actualType).as("Parameter should be of %s type, but it's of %s", type, actualType);

        return this;
    }

    public ParameterAssert isSerializable() {
        isInstanceOf(SerializableParameter.class);

        return this;
    }

    public static ParameterAssert assertThat(final Parameter actual) {
        return new ParameterAssert(actual);
    }

    private static <T> List<T> fetchEnums(final Property items) {
        try {
            return (List<T>) publicLookup().bind(items, "getEnum", MethodType.methodType(List.class)).invoke();
        } catch (final Throwable e) {
            throw new AssertionError(e);
        }
    }
}
