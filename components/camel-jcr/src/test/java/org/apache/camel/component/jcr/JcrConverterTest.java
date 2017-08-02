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
package org.apache.camel.component.jcr;

import java.io.ByteArrayInputStream;
import java.util.Calendar;
import javax.jcr.Value;

import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.impl.DefaultFactoryFinderResolver;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.util.ReflectionInjector;
import org.apache.camel.util.ServiceHelper;
import org.apache.jackrabbit.value.BinaryValue;
import org.apache.jackrabbit.value.BooleanValue;
import org.apache.jackrabbit.value.DateValue;
import org.apache.jackrabbit.value.StringValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for JCR type conversions ({@link JcrConverter})
 */
public class JcrConverterTest extends Assert {

    protected TypeConverter converter;

    @Before
    public void init() throws Exception {
        converter = new DefaultTypeConverter(new DefaultPackageScanClassResolver(),
                new ReflectionInjector(), new DefaultFactoryFinderResolver().resolveDefaultFactoryFinder(new DefaultClassResolver()), true);
        ServiceHelper.startService(converter);
    }

    @Test
    public void testBooleanValueConverter() throws Exception {
        assertJcrConverterAvailable(BooleanValue.class, Boolean.TRUE);
    }

    @Test
    public void testBinaryValueConverter() throws Exception {
        assertJcrConverterAvailable(BinaryValue.class, new ByteArrayInputStream("test".getBytes()));
    }

    @Test
    public void testDateValueConverter() throws Exception {
        assertJcrConverterAvailable(DateValue.class, Calendar.getInstance());
    }

    @Test
    public void testStringValueConverter() throws Exception {
        assertJcrConverterAvailable(StringValue.class, "plain text");
    }

    private void assertJcrConverterAvailable(Class<?> expected, Object object) {
        Value value = converter.convertTo(Value.class, object);
        assertNotNull(value);
        assertTrue(expected.isAssignableFrom(value.getClass()));
    }

}
