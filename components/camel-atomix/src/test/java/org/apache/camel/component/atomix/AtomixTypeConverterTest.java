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
package org.apache.camel.component.atomix;

import io.atomix.catalyst.transport.Address;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AtomixTypeConverterTest {

    @Test
    void testStringToAddressConversion() throws Exception {

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            TypeConverterRegistry registry = context.getTypeConverterRegistry();
            TypeConverter converter = registry.lookup(Address.class, String.class);

            assertNotNull(converter);
            assertEquals("127.0.0.1", converter.mandatoryConvertTo(Address.class, "127.0.0.1:1234").host());
            assertEquals(1234, converter.mandatoryConvertTo(Address.class, "127.0.0.1:1234").port());
        }
    }
}
