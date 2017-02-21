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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.SerializationDataFormat;
import org.apache.camel.impl.StringDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatFactory;

public class DataFormatFactoryTest extends ContextTestSupport {
    private static final DataFormat STRING_DF = new StringDataFormat("US-ASCII");
    private static final DataFormatFactory STRING_DFF = () -> new StringDataFormat("UTF-8");
    private static final DataFormat SERIALIZATION_DF = new SerializationDataFormat();

    public void testDataFormatResolveOrCreate() throws Exception {
        assertSame(STRING_DF, context.resolveDataFormat("string"));
        assertNotSame(STRING_DF, context.createDataFormat("string"));
        assertNotSame(context.createDataFormat("string"), context.createDataFormat("string"));
        
        assertSame(SERIALIZATION_DF, context.resolveDataFormat("serialization"));
        assertNotSame(SERIALIZATION_DF, context.createDataFormat("serialization"));
        assertNotSame(context.createDataFormat("serialization"), context.createDataFormat("serialization"));

        assertEquals("US-ASCII", ((StringDataFormat)context.resolveDataFormat("string")).getCharset());
        assertEquals("UTF-8", ((StringDataFormat)context.createDataFormat("string")).getCharset());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("string-dataformat", STRING_DF);
        registry.bind("string-dataformat-factory", STRING_DFF);
        registry.bind("serialization", SERIALIZATION_DF);

        return registry;
    }
}