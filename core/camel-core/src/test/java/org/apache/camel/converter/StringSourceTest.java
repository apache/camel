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
package org.apache.camel.converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.camel.TypeConverter;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ReflectionInjector;
import org.apache.camel.util.xml.StringSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringSourceTest {
    protected TypeConverter converter = new DefaultTypeConverter(
            new DefaultPackageScanClassResolver(), new ReflectionInjector(), false);
    protected String expectedBody = "<hello>world!</hello>";

    @BeforeEach
    public void setUp() throws Exception {
        ServiceHelper.startService(converter);
    }

    @Test
    public void testSerialization() throws Exception {
        StringSource expected = new StringSource(expectedBody, "mySystemID", "utf-8");
        expected.setPublicId("myPublicId");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(buffer);
        output.writeObject(expected);
        output.close();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        Object object = in.readObject();
        boolean b = object instanceof StringSource;
        assertTrue(b, "is a StringSource");
        StringSource actual = (StringSource) object;

        assertEquals(expected.getPublicId(), actual.getPublicId(), "source.text");
        assertEquals(expected.getSystemId(), actual.getSystemId(), "source.text");
        assertEquals(expected.getEncoding(), actual.getEncoding(), "source.text");
        assertEquals(expected.getText(), actual.getText(), "source.text");

        String value = converter.convertTo(String.class, actual);
        assertEquals(expectedBody, value, "text value of StringSource");
    }

}
