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

import org.apache.camel.component.jackson3.JacksonDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DefaultDataFormatResolverTest {

    private final DefaultDataFormatResolver resolver = new DefaultDataFormatResolver();

    @Test
    void shouldPreferTheJackson3LineWhenBothLinesAreOnTheClasspath() {
        // the service file names the Jackson 2.x class, and the Jackson 3.x class is on the classpath too
        DefaultCamelContext context = new DefaultCamelContext();

        DataFormat dataFormat = resolver.createDataFormat("jackson", context);

        assertInstanceOf(JacksonDataFormat.class, dataFormat);
    }

    @Test
    void shouldUseTheJackson2LineWhenItIsTheOnlyOne() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.setClassResolver(new Jackson2OnlyClassResolver());

        DataFormat dataFormat = resolver.createDataFormat("jackson", context);

        assertInstanceOf(org.apache.camel.component.jackson.JacksonDataFormat.class, dataFormat);
    }

    /**
     * Hides the Jackson 3.x classes, as a classpath with only camel-jackson.
     */
    static class Jackson2OnlyClassResolver extends DefaultClassResolver {

        @Override
        public Class<?> resolveClass(String name) {
            if (name.startsWith("org.apache.camel.component.jackson3")) {
                return null;
            }
            return super.resolveClass(name);
        }
    }
}
