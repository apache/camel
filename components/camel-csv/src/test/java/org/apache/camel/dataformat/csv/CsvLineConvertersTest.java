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
package org.apache.camel.dataformat.csv;

import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;

public class CsvLineConvertersTest {
    @Test
    public void shouldConvertAsList() {
        CsvLineConverter<?> converter = CsvLineConverters.getListConverter();

        Object result = converter.convertLine(new String[]{"foo", "bar"});

        assertTrue(result instanceof List);
        List list = (List) result;
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
    }

    @Test
    public void shouldConvertAsMap() {
        CsvLineConverter<?> converter = CsvLineConverters.getMapLineConverter(new String[]{"HEADER_1", "HEADER_2"});

        Object result = converter.convertLine(new String[]{"foo", "bar"});

        assertTrue(result instanceof Map);
        Map map = (Map) result;
        assertEquals(2, map.size());
        assertEquals("foo", map.get("HEADER_1"));
        assertEquals("bar", map.get("HEADER_2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotConvertAsMapWithNullHeaders() {
        CsvLineConverters.getMapLineConverter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotConvertAsMapWithNoHeaders() {
        CsvLineConverters.getMapLineConverter(new String[]{});
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotConvertAsMapWithInvalidLine() {
        CsvLineConverter<?> converter = CsvLineConverters.getMapLineConverter(new String[]{"HEADER_1", "HEADER_2"});

        converter.convertLine(new String[]{"foo"});
    }
}
