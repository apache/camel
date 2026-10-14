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

import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.CustomDataFormat;
import org.apache.camel.model.dataformat.DummyDataformat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
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
    public void testLabelOfDataFormatInstance() {
        DataFormatDefinition instance = new DataFormatDefinition(new DummyDataformat());
        assertEquals("marshal[DummyDataformat]", new MarshalDefinition(instance).getLabel());
        assertEquals("unmarshal[DummyDataformat]", new UnmarshalDefinition(instance).getLabel());
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
}
