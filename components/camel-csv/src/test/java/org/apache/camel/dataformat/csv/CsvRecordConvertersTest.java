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
package org.apache.camel.dataformat.csv;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * This class tests the common {@link CsvRecordConverter} implementations of
 * {@link org.apache.camel.dataformat.csv.CsvRecordConverters}.
 */
public class CsvRecordConvertersTest {
    private CSVRecord record;

    @Before
    public void setUp() throws Exception {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("A", "B", "C");
        CSVParser parser = new CSVParser(new StringReader("1,2,3"), format);
        List<CSVRecord> records = parser.getRecords();
        record = records.get(0);
    }

    @Test
    public void shouldConvertAsList() {
        List<String> list = CsvRecordConverters.listConverter().convertRecord(record);

        assertNotNull(list);
        assertEquals(3, list.size());
        assertEquals("1", list.get(0));
        assertEquals("2", list.get(1));
        assertEquals("3", list.get(2));
    }

    @Test
    public void shouldConvertAsMap() {
        Map<String, String> map = CsvRecordConverters.mapConverter().convertRecord(record);

        assertNotNull(map);
        assertEquals(3, map.size());
        assertEquals("1", map.get("A"));
        assertEquals("2", map.get("B"));
        assertEquals("3", map.get("C"));
    }
}
