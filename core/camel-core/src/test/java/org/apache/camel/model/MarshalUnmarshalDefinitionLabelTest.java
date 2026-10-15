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
package org.apache.camel.model;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.CustomDataFormat;
import org.apache.camel.model.dataformat.DummyDataformat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MarshalUnmarshalDefinitionLabelTest {

    @Test
    public void testLabelShowsDataFormatName() {
        assertEquals("unmarshal[jackson]", new UnmarshalDefinition(new JsonDataFormat()).getLabel());
        assertEquals("Unmarshal[jackson]", new UnmarshalDefinition(new JsonDataFormat()).toString());
        assertEquals("marshal[gson]", new MarshalDefinition(new JsonDataFormat(JsonLibrary.Gson)).getLabel());
        assertEquals("Marshal[gson]", new MarshalDefinition(new JsonDataFormat(JsonLibrary.Gson)).toString());
        assertEquals("marshal[csv]", new MarshalDefinition(new CsvDataFormat()).getLabel());
        assertEquals("unmarshal[csv]", new UnmarshalDefinition(new CsvDataFormat()).getLabel());
    }

    @Test
    public void testLabelOfDataFormatInstanceWithName() {
        // DummyDataformat declares its name with DataFormatName
        DataFormatDefinition instance = new DataFormatDefinition(new DummyDataformat());
        assertEquals("marshal[dummy]", new MarshalDefinition(instance).getLabel());
        assertEquals("unmarshal[dummy]", new UnmarshalDefinition(instance).getLabel());
    }

    @Test
    public void testLabelOfDataFormatInstanceWithoutName() {
        DataFormatDefinition instance = new DataFormatDefinition(new MyDataFormat());
        assertEquals("marshal[MyDataFormat]", new MarshalDefinition(instance).getLabel());
        assertEquals("unmarshal[MyDataFormat]", new UnmarshalDefinition(instance).getLabel());
    }

    @Test
    public void testLabelOfCustomDataFormat() {
        assertEquals("marshal[CustomDataFormat[myFormat]]", new MarshalDefinition(new CustomDataFormat("myFormat")).getLabel());
        assertEquals("unmarshal[CustomDataFormat[myFormat]]",
                new UnmarshalDefinition(new CustomDataFormat("myFormat")).getLabel());
    }

    @Test
    public void testLabelWithoutDataFormat() {
        assertEquals("marshal[]", new MarshalDefinition().getLabel());
        assertEquals("unmarshal[]", new UnmarshalDefinition().getLabel());
    }

    private static class MyDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) {
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) {
            return null;
        }
    }
}
