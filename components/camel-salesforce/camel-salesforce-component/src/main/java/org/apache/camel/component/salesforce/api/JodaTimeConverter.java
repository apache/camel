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
package org.apache.camel.component.salesforce.api;

import java.lang.reflect.Constructor;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

/**
 * XStream converter for handling JodaTime fields.
 */
public class JodaTimeConverter implements Converter {

    private final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendYear(4, 4)
            .appendLiteral('-')
            .appendMonthOfYear(2)
            .appendLiteral('-')
            .appendDayOfMonth(2)
            .appendLiteral('T')
            .appendHourOfDay(2)
            .appendLiteral(':')
            .appendMinuteOfHour(2)
            .appendLiteral(':')
            .appendSecondOfMinute(2)
            .appendLiteral('.')
            .appendMillisOfSecond(3)
            .appendTimeZoneOffset("Z", true, 2, 2)
            .toFormatter();

    @Override
    public void marshal(Object o, HierarchicalStreamWriter writer, MarshallingContext context) {
        DateTime dateTime = (DateTime) o;
        writer.setValue(formatter.print(dateTime));
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String dateTimeStr = reader.getValue();
        Class<?> requiredType = context.getRequiredType();
        try {
            Constructor<?> constructor = requiredType.getConstructor(Object.class, DateTimeZone.class);
            // normalize date time to UTC
            return constructor.newInstance(dateTimeStr, DateTimeZone.UTC);
        } catch (Exception e) {
            throw new ConversionException(
                    String.format("Error reading Joda DateTime from value %s: %s",
                            dateTimeStr, e.getMessage()),
                    e);
        }
    }

    @Override
    public boolean canConvert(Class aClass) {
        return DateTime.class.isAssignableFrom(aClass);
    }

}
