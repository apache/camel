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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import facebook4j.Reading;
import org.apache.camel.component.facebook.FacebookConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test {@link ReadingBuilder}.
 */
public class ReadingBuilderTest {

    @Test
    public void testCopy() throws Exception {
        final Reading source = new Reading();
        source.fields("field1", "field2");
        source.filter("testFilter");
        source.limit(100);
        source.locale(Locale.US);
        source.metadata();
        source.offset(1000);
        source.since(new Date());
        source.until(new Date());
        source.withLocation();

        Reading copy = ReadingBuilder.copy(source, false);
        assertNotNull(copy, "Null copy");
        assertEquals(source.toString(), copy.toString(), "Copy not equal");

        // skip since and until
        copy = ReadingBuilder.copy(source, true);
        assertNotEquals("Copy equal", source.toString(), copy.toString());
        assertFalse(copy.toString().contains("since="), "since");
        assertFalse(copy.toString().contains("until="), "until");
    }

    @Test
    public void testSetProperties() {
        assertDoesNotThrow(() -> setProperties());
    }

    private void setProperties() {
        final Reading reading = new Reading();

        Map<String, Object> properties = new HashMap<>();
        properties.put("fields", "field1,field2");
        properties.put("filter", "testFilter");
        properties.put("limit", "100");
        properties.put("metadata", "");
        properties.put("offset", "1000");
        final String facebookDate = new SimpleDateFormat(FacebookConstants.FACEBOOK_DATE_FORMAT).format(new Date());
        properties.put("since", facebookDate);
        properties.put("until", "arbitrary date, to be validated by Facebook call");
        properties.put("withLocation", "");

        // set properties on Reading
        ReadingBuilder.setProperties(reading, properties);
    }

}
