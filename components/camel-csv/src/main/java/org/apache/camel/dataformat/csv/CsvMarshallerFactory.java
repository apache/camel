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

import org.apache.commons.csv.CSVFormat;

/**
 * A {@link CsvMarshaller} factory.
 */
public interface CsvMarshallerFactory {

    CsvMarshallerFactory DEFAULT = new CsvMarshallerFactory() {
        @Override
        public CsvMarshaller create(CSVFormat format, CsvDataFormat dataFormat) {
            return CsvMarshaller.create(format, dataFormat);
        }
    };

    /**
     * Creates and returns a new {@link CsvMarshaller}.
     *
     * @param format     the <b>CSV</b> format. Can NOT be <code>null</code>.
     * @param dataFormat the <b>CSV</b> data format. Can NOT be <code>null</code>.
     * @return a new {@link CsvMarshaller}. Never <code>null</code>.
     */
    CsvMarshaller create(CSVFormat format, CsvDataFormat dataFormat);
}
