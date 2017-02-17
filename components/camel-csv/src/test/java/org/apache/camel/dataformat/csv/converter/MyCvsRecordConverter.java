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
package org.apache.camel.dataformat.csv.converter;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.dataformat.csv.CsvRecordConverter;
import org.apache.commons.csv.CSVRecord;

/**
 * Test {@link CsvRecordConverter} implementation.
 * <p>
 * This implementation is explicitely created in a subpackage to check the
 * visibility of {@link CsvRecordConverter}.
 * </p>
 */
public class MyCvsRecordConverter implements CsvRecordConverter<List<String>> {

    private final String[] record;

    public MyCvsRecordConverter(String... record) {
        assert record != null : "Unspecified record";
        this.record = record;
    }

    @Override
    public List<String> convertRecord(CSVRecord record) {
        assert record != null : "Unspecified record";
        return Arrays.asList(this.record);
    }
}
