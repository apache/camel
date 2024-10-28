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

import java.io.BufferedReader;

import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smooks.io.sink.StringSink;
import org.smooks.io.source.ReaderSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SinkConverterTest {

    private TypeConverter typeConverter;

    @BeforeEach
    public void beforeEach() {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        typeConverter = camelContext.getTypeConverter();
    }

    @Test
    public void convertStringResultToStreamSource() throws Exception {
        StringSink stringResult = createStringSink("Bajja");

        ReaderSource<?> streamSource = typeConverter.convertTo(ReaderSource.class, stringResult);

        BufferedReader reader = new BufferedReader(streamSource.getReader());
        assertEquals("Bajja", reader.readLine());
    }

    private StringSink createStringSink(String string) {
        StringSink stringResult = new StringSink();
        stringResult.getStringWriter().write(string);
        return stringResult;
    }

}
