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
import org.apache.camel.processor.transformer.UnchangedDataTypeTransformer;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultTransformerRegistryTest {

    private final DefaultTransformerRegistry dataTypeRegistry = new DefaultTransformerRegistry(new DefaultCamelContext());

    @Test
    public void shouldLookupDefaultDataTypeTransformers() {
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

    @Test
    public void shouldLazyLoadToTransformerBeforeFromTransformer() {
        // load binary and application-x-unchanged first, as a Camel context does up front: the transformer of the
        // from data type must not be taken for a to data type that is not loaded yet (CAMEL-27289)
        loadTransformers("application-octet-stream", "application-x-unchanged");
        TransformerKey key = new TransformerKey(new DataType("application-octet-stream"), new DataType("lowercase"));
        Transformer transformer = dataTypeRegistry.resolveTransformer(key);
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(LowercaseDataTypeTransformer.class, transformer.getClass());
        // and the same again, from the cache
        transformer = dataTypeRegistry.resolveTransformer(key);
        Assertions.assertEquals(LowercaseDataTypeTransformer.class, transformer.getClass());

        transformer = dataTypeRegistry.resolveTransformer(
                new TransformerKey(new DataType("application-x-unchanged"), new DataType("uppercase")));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(UppercaseDataTypeTransformer.class, transformer.getClass());
    }

    @Test
    public void shouldFallBackToFromTransformer() {
        // no transformer of the to data type, neither loaded nor to lazy load: the one of the from data type is used
        loadTransformers("text-plain", "application-x-unchanged");
        Transformer transformer = dataTypeRegistry.resolveTransformer(
                new TransformerKey(new DataType("text-plain"), new DataType("java:" + Integer.class.getName())));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(StringDataTypeTransformer.class, transformer.getClass());

        transformer = dataTypeRegistry.resolveTransformer(
                new TransformerKey(new DataType("application-x-unchanged"), new DataType("no-such-type")));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(UnchangedDataTypeTransformer.class, transformer.getClass());
    }

    private void loadTransformers(String... names) {
        for (String name : names) {
            Assertions.assertNotNull(dataTypeRegistry.resolveTransformer(new TransformerKey(name)));
        }
    }

}
