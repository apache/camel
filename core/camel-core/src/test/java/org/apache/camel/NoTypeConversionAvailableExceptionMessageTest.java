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
package org.apache.camel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message of {@link NoTypeConversionAvailableException}: "null" as the from type means the value is null, and says
 * so; a class without a canonical name (an anonymous class) shows its binary name instead of "null".
 */
class NoTypeConversionAvailableExceptionMessageTest {

    @Test
    void nullValueSaysTheValueIsNull() {
        assertThat(new NoTypeConversionAvailableException(null, java.io.InputStream.class).getMessage())
                .isEqualTo("No type converter available to convert from type: null to the required type: "
                           + "java.io.InputStream (the value is null)");
    }

    @Test
    void namedClassUsesCanonicalName() {
        assertThat(NoTypeConversionAvailableException.createMessage("hello", Integer.class))
                .isEqualTo("No type converter available to convert from type: java.lang.String to the required type: "
                           + "java.lang.Integer");
    }

    @Test
    void anonymousClassUsesBinaryName() {
        Object anon = new Runnable() {
            @Override
            public void run() {
            }
        };

        assertThat(NoTypeConversionAvailableException.createMessage(anon, String.class))
                .doesNotContain("from type: null")
                .contains("from type: " + anon.getClass().getName());
        assertThat(NoTypeConversionAvailableException.createMessage(anon, String.class, new RuntimeException("boom")))
                .doesNotContain("from type: null")
                .contains("from type: " + anon.getClass().getName());
    }

    @Test
    void messageConstructorKeepsValueAndType() {
        NoTypeConversionAvailableException e
                = new NoTypeConversionAvailableException("nothing to convert", null, String.class);

        assertThat(e.getMessage()).isEqualTo("nothing to convert");
        assertThat(e.getValue()).isNull();
        assertThat(e.getToType()).isEqualTo(String.class);
    }
}
