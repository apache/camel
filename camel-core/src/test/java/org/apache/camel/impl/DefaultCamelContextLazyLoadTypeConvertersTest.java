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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.TypeConverter;

/**
 * Tests the lazy loading property of the camel context. The default behavior is to
 * load all of the type converters up front. When the lazy load property is enabled
 * on the context, the loading will be deferred until the converters are accessed.
 */
public class DefaultCamelContextLazyLoadTypeConvertersTest extends TestSupport {

    private CamelContext context = new DefaultCamelContext();

    @SuppressWarnings("deprecation")
    public void testLazyLoadDefault() throws Exception {
        assertFalse("Default setting should have been true", context.isLazyLoadTypeConverters());
    }

    @SuppressWarnings("deprecation")
    public void testConvertLoadUpFront() throws Exception {
        context.setLazyLoadTypeConverters(false);
        doConvertTest();
    }

    @SuppressWarnings("deprecation")
    public void testConvertLazyLoad() throws Exception {
        context.setLazyLoadTypeConverters(true);
        doConvertTest();
    }
    
    private void doConvertTest() throws Exception {
        context.start();
        convert();
        context.stop();
        convert();
    }

    private void convert() throws Exception {
        TypeConverter converter = context.getTypeConverter();
        Integer value = converter.convertTo(Integer.class, "1000");
        assertNotNull(value);
        assertEquals("Converted to Integer", new Integer(1000), value);

        String text = converter.convertTo(String.class, value);
        assertEquals("Converted to String", "1000", text);
    }
    
}
