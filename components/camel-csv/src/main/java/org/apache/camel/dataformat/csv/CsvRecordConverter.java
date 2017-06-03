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

import org.apache.commons.csv.CSVRecord;

/**
 * This interface is used to define a converter that transform a {@link org.apache.commons.csv.CSVRecord} into another
 * type.
 * <p/>
 * The {@link org.apache.camel.dataformat.csv.CsvRecordConverters} class defines common converters.
 *
 * @param <T> Conversion type
 * @see org.apache.camel.dataformat.csv.CsvRecordConverters
 */
public interface CsvRecordConverter<T> {
    /**
     * Converts the CSV record into another type.
     *
     * @param record CSV record to convert
     * @return converted CSV record
     */
    T convertRecord(CSVRecord record);
}
