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
package org.apache.camel.model.dataformat;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.BitSet;

import javax.xml.bind.annotation.XmlAttribute;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataFormat;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test cases for corresponding class {@link CsvDataFormat}.
 */
public class CsvDataFormatTest {

    @Test
    public void testConfigureDataFormatDataFormatCamelContext() {
        CsvDataFormat csvDataFormat = new CsvDataFormat();
        csvDataFormat.setIgnoreHeaderCase(true);
        csvDataFormat.setTrim(true);
        csvDataFormat.setTrailingDelimiter(true);
        MyDataFormat dataFormat = new MyDataFormat();
        DefaultCamelContext camelContext = new DefaultCamelContext();
        csvDataFormat.configureDataFormat(dataFormat, camelContext);
        assertEquals(3, dataFormat.bitSet.cardinality());
        csvDataFormat.setIgnoreHeaderCase(false);
        csvDataFormat.setTrim(false);
        csvDataFormat.setTrailingDelimiter(false);
        csvDataFormat.configureDataFormat(dataFormat, camelContext);
        assertEquals(dataFormat.bitSet.cardinality(), 0);
    }

    //
    // Helper classes
    //

    static final class MyDataFormat implements DataFormat {

        final BitSet bitSet = new BitSet();

        MyDataFormat() {
        }

        public void setIgnoreHeaderCase(Boolean ignoreHeaderCase) {
            bitSet.set(0, ignoreHeaderCase);
        }

        public void setTrim(Boolean trim) {
            bitSet.set(2, trim);
        }

        public void setTrailingDelimiter(Boolean trailingDelimiter) {
            bitSet.set(3, trailingDelimiter);
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

}
