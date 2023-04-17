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
package org.apache.camel.converter;

import org.apache.camel.impl.converter.EnumTypeConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BasicEnumConverterTest {

    private final EnumTypeConverter converter = new EnumTypeConverter();

    @Test
    public void testConvertFromString() {
        StatusCodeEnum code = converter.convertTo(StatusCodeEnum.class, "OK");
        assertEquals(StatusCodeEnum.OK, code, "String should be converted to corresponding Enum value");
    }

    @Test
    public void testConvertFromStringCaseInsensitive() {
        StatusCodeEnum code = converter.convertTo(StatusCodeEnum.class, "ok");
        assertEquals(StatusCodeEnum.OK, code, "Lower case string should be converted to corresponding Enum value");
    }

    @Test
    public void testConvertFromCamelCasedString() {
        StatusCodeEnum code = converter.convertTo(StatusCodeEnum.class, "NotFound");
        assertEquals(StatusCodeEnum.NOT_FOUND, code, "Camel cased string should be converted to corresponding Enum value");
    }

    @Test
    public void testConvertFromDashedString() {
        StatusCodeEnum code = converter.convertTo(StatusCodeEnum.class, "not-found");
        assertEquals(StatusCodeEnum.NOT_FOUND, code, "Dashed string should be converted to corresponding Enum value");
    }

    private enum StatusCodeEnum {
        OK,
        NOT_FOUND;
    }
}
