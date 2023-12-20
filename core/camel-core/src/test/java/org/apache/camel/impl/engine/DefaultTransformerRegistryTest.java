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

package org.apache.camel.impl.engine;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.transformer.ByteArrayDataTypeTransformer;
import org.apache.camel.processor.transformer.StringDataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultTransformerRegistryTest {

    private final DefaultTransformerRegistry dataTypeRegistry = new DefaultTransformerRegistry(new DefaultCamelContext());

    @Test
    public void shouldLookupDefaultDataTypeTransformers() throws Exception {
        Transformer transformer = dataTypeRegistry.resolveTransformer(new TransformerKey("text-plain"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(StringDataTypeTransformer.class, transformer.getClass());
        transformer = dataTypeRegistry.resolveTransformer(new TransformerKey("application-octet-stream"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(ByteArrayDataTypeTransformer.class, transformer.getClass());
        transformer = dataTypeRegistry.resolveTransformer(new TransformerKey("lowercase"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(LowercaseDataTypeTransformer.class, transformer.getClass());
        transformer = dataTypeRegistry.resolveTransformer(new TransformerKey("uppercase"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(UppercaseDataTypeTransformer.class, transformer.getClass());
    }

}
