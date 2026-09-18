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
package org.apache.camel.maven.packaging;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The signatures the API reference lists read like source: simple type names, generics, parameter names, varargs and
 * throws kept.
 */
public class GenerateApiReferenceMojoTest {

    interface Sample {

        Object getHeader(String name);

        <T> T getHeader(String name, Class<T> type);

        Object getHeader(String name, Supplier<Object> defaultValueSupplier);

        boolean removeHeaders(String pattern, String... excludePatterns);

        Map<String, Object> getHeaders();

        <T extends Number> List<? extends T> numbers(Map.Entry<String, T> entry) throws IOException, IllegalStateException;

        void setBody(Object body);
    }

    private static Method method(String name, Class<?>... types) throws NoSuchMethodException {
        return Sample.class.getMethod(name, types);
    }

    @Test
    public void signaturesReadLikeSource() throws Exception {
        assertThat(GenerateApiReferenceMojo.signature(method("getHeader", String.class)))
                .isEqualTo("Object getHeader(String name)");
        assertThat(GenerateApiReferenceMojo.signature(method("getHeader", String.class, Class.class)))
                .isEqualTo("<T> T getHeader(String name, Class<T> type)");
        assertThat(GenerateApiReferenceMojo.signature(method("getHeader", String.class, Supplier.class)))
                .isEqualTo("Object getHeader(String name, Supplier<Object> defaultValueSupplier)");
        assertThat(GenerateApiReferenceMojo.signature(method("removeHeaders", String.class, String[].class)))
                .isEqualTo("boolean removeHeaders(String pattern, String... excludePatterns)");
        assertThat(GenerateApiReferenceMojo.signature(method("getHeaders")))
                .isEqualTo("Map<String, Object> getHeaders()");
        assertThat(GenerateApiReferenceMojo.signature(method("numbers", Map.Entry.class)))
                .isEqualTo("<T extends Number> List<? extends T> numbers(Map.Entry<String, T> entry)"
                           + " throws IOException, IllegalStateException");
        assertThat(GenerateApiReferenceMojo.signature(method("setBody", Object.class)))
                .isEqualTo("void setBody(Object body)");
    }
}
