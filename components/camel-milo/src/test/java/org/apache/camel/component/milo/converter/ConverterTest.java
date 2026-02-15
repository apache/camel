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
package org.apache.camel.component.milo.converter;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConverterTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ConverterTest.class);

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    @Test
    public void testDataValueToVariant() {
        final Variant value = testConvertDataValue("Foo", Variant.class);
        assertNotNull(value);
        assertEquals("Foo", value.getValue());
    }

    @Test
    public void testVariantToDataValue() {
        final DataValue value = testConvert(new Variant("Foo"), DataValue.class);
        assertNotNull(value);
        assertEquals("Foo", value.getValue().getValue());
        assertTrue(value.getStatusCode().isGood());
    }

    private <T> T testConvert(final Object value, final Class<T> clazz) {
        return this.context.getTypeConverter().convertTo(clazz, value);
    }

    private <T> T testConvertDataValue(final Object value, final Class<T> clazz) {
        return this.context.getTypeConverter().convertTo(clazz, new DataValue(new Variant(value)));
    }
}
