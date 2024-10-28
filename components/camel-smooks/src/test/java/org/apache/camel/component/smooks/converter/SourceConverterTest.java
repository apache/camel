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
package org.apache.camel.component.smooks.converter;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smooks.io.source.JavaSourceWithoutEventStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SourceConverterTest {

    private TypeConverter typeConverter;

    @BeforeEach
    public void beforeEach() {
        CamelContext camelContext = new DefaultCamelContext();
        typeConverter = camelContext.getTypeConverter();
    }

    @Test
    public void convertStringToJavaSourceWithoutEventStream() {
        final String payload = "dummyPayload";
        final JavaSourceWithoutEventStream javaSource = typeConverter.convertTo(JavaSourceWithoutEventStream.class, payload);
        final Map<String, Object> beans = javaSource.getBeans();
        final String actualPayload = (String) beans.get("string");

        assertThat(payload, is(actualPayload));
    }
}
