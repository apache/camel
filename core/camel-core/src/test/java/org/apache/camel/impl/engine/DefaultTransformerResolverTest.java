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

import org.apache.camel.Message;
import org.apache.camel.component.jackson3.transform.JsonDataTypeTransformer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.transformer.StringDataTypeTransformer;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTransformerResolverTest {

    private DefaultCamelContext camelContext;

    private final DefaultTransformerResolver resolver = new DefaultTransformerResolver();

    @BeforeEach
    void setup() {
        this.camelContext = new DefaultCamelContext();
    }

    @Test
    public void shouldHandleUnresolvableDataTypeTransformers() {
        Transformer transformer = resolver.resolve(new TransformerKey("unknown"), camelContext);
        Assertions.assertNull(transformer);

        transformer = resolver.resolve(new TransformerKey(
                new DataType("foo:fromType"),
                new DataType("foo:toType")), camelContext);
        Assertions.assertNull(transformer);
    }

    @Test
    public void shouldResolveDataTypeTransformers() {
        Transformer transformer = resolver.resolve(new TransformerKey("text-plain"), camelContext);
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(StringDataTypeTransformer.class, transformer.getClass());

        transformer = resolver.resolve(new TransformerKey("lowercase"), camelContext);
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(LowercaseDataTypeTransformer.class, transformer.getClass());

        transformer = resolver.resolve(new TransformerKey(new DataType("foo"), new DataType("json")), camelContext);
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(FooDataTypeTransformer.class, transformer.getClass());
    }

    @Test
    public void shouldPreferTheJackson3LineWhenBothLinesAreOnTheClasspath() {
        // the service file names the Jackson 2.x class, and the Jackson 3.x class is on the classpath too
        Transformer transformer = resolver.resolve(new TransformerKey("application-json"), camelContext);
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(JsonDataTypeTransformer.class, transformer.getClass());
    }

    @Test
    public void shouldUseTheJackson2LineWhenItIsTheOnlyOne() {
        camelContext.setClassResolver(new DefaultDataFormatResolverTest.Jackson2OnlyClassResolver());

        Transformer transformer = resolver.resolve(new TransformerKey("application-json"), camelContext);
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(org.apache.camel.component.jackson.transform.JsonDataTypeTransformer.class,
                transformer.getClass());
    }

    @DataTypeTransformer(name = "foo-json", fromType = "foo", toType = "json")
    public static class FooDataTypeTransformer extends Transformer {

        @Override
        public void transform(Message message, DataType fromType, DataType toType) {
            message.setBody("Foo");
        }
    }
}
