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
package org.apache.camel.component.facebook.data;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import facebook4j.Reading;

/**
 * Builds {@link facebook4j.Reading} instances.
 */
public final class ReadingBuilder {

    private ReadingBuilder() {
        // Helper class
    }

    public static Reading copy(Reading reading, boolean skipSinceUtil) throws NoSuchFieldException, IllegalAccessException {
        // use private field access to make a copy
        Field field = Reading.class.getDeclaredField("parameterMap");
        field.setAccessible(true);
        final Map<String, String> source = (Map<String, String>) field.get(reading);
        // create another reading, and add all fields from source
        Reading copy = new Reading();
        final Map<String, String> copyMap = new LinkedHashMap<>(source);
        if (skipSinceUtil) {
            copyMap.remove("since");
            copyMap.remove("until");
        }
        field.set(copy, copyMap);
        field.setAccessible(false);
        return copy;
    }

    /**
     * Sets Reading properties.
     *
     * @param reading           Reading object to populate
     * @param readingProperties Map to extract properties
     */
    public static void setProperties(Reading reading, Map<String, Object> readingProperties) {

        final String fields = (String) readingProperties.remove("fields");
        if (fields != null) {
            reading.fields(fields.toString().split(","));
        }
        final Object limit = readingProperties.remove("limit");
        if (limit != null) {
            reading.limit(Integer.parseInt(limit.toString()));
        }
        final Object offset = readingProperties.remove("offset");
        if (offset != null) {
            reading.offset(Integer.parseInt(offset.toString()));
        }
        final Object until = readingProperties.remove("until");
        if (until != null) {
            // take the string form as is to support PHP strtotime, no validation until API call!
            reading.until(until.toString());
        }
        final Object since = readingProperties.remove("since");
        if (since != null) {
            // take the string form as is to support PHP strtotime, no validation until API call!
            reading.since(since.toString());
        }
        final Object metadata = readingProperties.remove("metadata");
        if (metadata != null && Boolean.parseBoolean(metadata.toString())) {
            reading.metadata();
        }
        final Object locale = readingProperties.remove("locale");
        if (locale != null) {
            String[] args = locale.toString().split(",");
            switch (args.length) {
                case 1:
                    reading.locale(new Locale(args[0]));
                    break;
                case 2:
                    reading.locale(new Locale(args[0], args[1]));
                    break;
                case 3:
                    reading.locale(new Locale(args[0], args[1], args[2]));
                    break;
                default:
                    throw new IllegalArgumentException(
                            String.format("Invalid value for property 'locale' %s, "
                                          + "must be of the form [language][,country][,variant]",
                                    locale.toString()));
            }
        }
        final Object with = readingProperties.remove("with");
        if (with != null && Boolean.parseBoolean(with.toString())) {
            reading.withLocation();
        }
        final Object filter = readingProperties.remove("filter");
        if (filter != null) {
            reading.filter(filter.toString());
        }
    }

    public static Reading merge(Reading reading, Reading readingUpdate) throws NoSuchFieldException, IllegalAccessException {
        Reading mergedReading = new Reading();

        Field field = Reading.class.getDeclaredField("parameterMap");
        field.setAccessible(true);
        final Map<String, Object> readingParameters = (Map<String, Object>) field.get(reading);
        readingParameters.putAll((Map<String, Object>) field.get(readingUpdate));
        field.setAccessible(false);

        setProperties(mergedReading, readingParameters);

        return mergedReading;
    }
}
