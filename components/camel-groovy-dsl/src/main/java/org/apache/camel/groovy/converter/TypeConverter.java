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
package org.apache.camel.groovy.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import groovy.lang.GString;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.ObjectConverter;

/**
 * TypeConverter for Groovy GStrings.
 */
@Converter
public final class TypeConverter {

    private TypeConverter() {

    }

    @Converter
    public static InputStream toInputStream(GString value, Exchange exchange) throws IOException {
        return IOConverter.toInputStream(value.toString(), exchange);
    }

    @Converter
    public static byte[] toByteArray(GString value, Exchange exchange) throws IOException {
        return IOConverter.toByteArray(value.toString(), exchange);
    }

    @Converter
    public static StringReader toReader(GString value) {
        return IOConverter.toReader(value.toString());
    }

    @Converter
    public static char toChar(GString value) {
        return ObjectConverter.toChar(value.toString());
    }

    @Converter
    public static Integer toInteger(GString value) {
        return ObjectConverter.toInteger(value.toString());
    }

    @Converter
    public static Long toLong(GString value) {
        return ObjectConverter.toLong(value.toString());
    }

    @Converter
    public static Float toFloat(GString value) {
        return ObjectConverter.toFloat(value.toString());
    }

    @Converter
    public static Double toDouble(GString value) {
        return ObjectConverter.toDouble(value.toString());
    }

    @Converter
    public static Boolean toBoolean(GString value) {
        return ObjectConverter.toBoolean(value.toString());
    }
}
