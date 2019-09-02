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
package org.apache.camel.component.jcr;

import java.io.InputStream;
import java.util.Calendar;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.camel.Converter;
import org.apache.jackrabbit.value.BinaryValue;
import org.apache.jackrabbit.value.BooleanValue;
import org.apache.jackrabbit.value.DateValue;
import org.apache.jackrabbit.value.StringValue;

/**
 * A helper class to transform Object into JCR {@link Value} implementations 
 */
@Converter(generateLoader = true)
public final class JcrConverter {

    private JcrConverter() {
    }

    @Converter
    public static Value toValue(Boolean bool) {
        return new BooleanValue(bool);
    }

    @Converter
    public static Value toValue(InputStream stream) {
        return new BinaryValue(stream);
    }

    @Converter
    public static Value toValue(Calendar calendar) {
        return new DateValue(calendar);
    }

    @Converter
    public static Value toValue(String value) {
        return new StringValue(value);
    }

    @Converter
    public static Boolean toBoolean(Value value) throws RepositoryException {
        return value.getBoolean();
    }

    @Converter
    public static InputStream toValue(Value value) throws RepositoryException {
        return value.getBinary().getStream();
    }

    @Converter
    public static Calendar toCalendar(Value value) throws RepositoryException {
        return value.getDate();
    }

    @Converter
    public static String toString(Value value) throws RepositoryException {
        return value.getString();
    }

}
