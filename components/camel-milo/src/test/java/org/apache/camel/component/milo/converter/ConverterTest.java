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
package org.apache.camel.component.milo.converter;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.Assert;
import org.junit.Test;

public class ConverterTest extends CamelTestSupport {

    @Test
    public void testDataValueToVariant() {
        final Variant value = testConvertDataValue("Foo", Variant.class);
        Assert.assertNotNull(value);
        Assert.assertEquals("Foo", value.getValue());
    }

    @Test
    public void testVariantToDataValue() {
        final DataValue value = testConvert(new Variant("Foo"), DataValue.class);
        Assert.assertNotNull(value);
        Assert.assertEquals("Foo", value.getValue().getValue());
        Assert.assertTrue(value.getStatusCode().isGood());
    }

    private <T> T testConvert(final Object value, final Class<T> clazz) {
        return this.context.getTypeConverter().convertTo(clazz, value);
    }

    private <T> T testConvertDataValue(final Object value, final Class<T> clazz) {
        return this.context.getTypeConverter().convertTo(clazz, new DataValue(new Variant(value)));
    }
}
