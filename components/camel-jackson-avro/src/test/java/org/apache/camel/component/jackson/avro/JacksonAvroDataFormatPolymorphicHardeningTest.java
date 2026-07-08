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
package org.apache.camel.component.jackson.avro;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the AvroMapper created by {@link JacksonAvroDataFormat} enables
 * {@link MapperFeature#BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES} by default — the Jackson mechanism that refuses unsafe
 * polymorphic base types (e.g. Object/Serializable) when polymorphic/default typing is enabled, as defense-in-depth
 * against gadget-chain deserialization.
 */
public class JacksonAvroDataFormatPolymorphicHardeningTest {

    @Test
    void blockUnsafePolymorphicBaseTypesEnabledByDefault() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            JacksonAvroDataFormat df = new JacksonAvroDataFormat();
            df.setCamelContext(context);
            df.start();
            ObjectMapper mapper = df.getObjectMapper();
            assertNotNull(mapper);
            assertTrue(mapper.isEnabled(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES),
                    "camel-jackson-avro data format must enable BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES by default");
        }
    }
}
