/**
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
package org.apache.camel.component.jackson;

import java.util.HashMap;

import static java.util.Collections.singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.camel.component.jackson.converter.JacksonTypeConverters.convertTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class JacksonConversionsSimpleTest extends Assert {

    @Test
    public void shouldNotConvertMapToString() {
        Object convertedObject = convertTo(String.class, null, new HashMap<String, String>(), null);
        assertNull(convertedObject);
    }

    @Test
    public void shouldNotConvertMapToNumber() {
        Object convertedObject = convertTo(Long.class, null, new HashMap<String, String>(), null);
        assertNull(convertedObject);
    }

    @Test
    public void shouldNotConvertMapToPrimitive() {
        Object convertedObject = convertTo(long.class, null, new HashMap<String, String>(), null);
        assertNull(convertedObject);
    }

    @Test
    public void shouldResolveMapperFromRegistry() {
        // Given
        Exchange exchange = mock(Exchange.class, RETURNS_DEEP_STUBS);
        ObjectMapper mapper = mock(ObjectMapper.class);
        given(exchange.getContext().getRegistry().findByType(eq(ObjectMapper.class))).willReturn(singleton(mapper));

        // When
        convertTo(TestPojo.class, exchange, new HashMap<String, String>(), null);

        // Then
        verify(mapper).canSerialize(TestPojo.class);
    }

}
