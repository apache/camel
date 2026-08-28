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
 * Verifies {@link TypeConversionException#createMessage} handles anonymous and local classes whose
 * {@link Class#getCanonicalName()} returns {@code null}, and that the message never calls {@code toString()} on the
 * value (which could OOM for huge payloads).
 *
 * <p>
 * Real-world trigger: SFTP stream body is an anonymous inner class ({@code ChannelSftp$2}); its canonical name is
 * {@code null}, causing the error message to print {@code "from type: null"} instead of the actual class name.
 */
class TypeConversionExceptionMessageTest {

    @Test
    void createMessage_anonymousClassUsesGetName() {
        // anonymous Runnable — getCanonicalName() returns null
        Object anon = new Runnable() {
            @Override
            public void run() {
            }
        };
        assertThat(anon.getClass().getCanonicalName()).isNull();

        String msg = TypeConversionException.createMessage(anon, String.class, new RuntimeException("boom"));

        assertThat(msg)
                .doesNotContain("from type: null")
                .contains("from type: ")
                .contains("$"); // binary name contains $ for anonymous classes
    }

    @Test
    void createMessage_namedClassUsesCanonicalName() {
        String msg = TypeConversionException.createMessage("hello", Integer.class, new RuntimeException("oops"));

        assertThat(msg)
                .contains("from type: java.lang.String")
                .contains("to the required type: java.lang.Integer")
                .contains("due to java.lang.RuntimeException: oops");
    }

    @Test
    void createMessage_nullValueShowsNull() {
        String msg = TypeConversionException.createMessage(null, String.class, new RuntimeException("npe"));

        assertThat(msg)
                .contains("from type: null")
                .contains("to the required type: java.lang.String");
    }

    @Test
    void createMessage_doesNotCallToStringOnValue() {
        // Fails immediately if exception construction invokes body.toString()
        Object body = new Object() {
            @Override
            public String toString() {
                throw new AssertionError("TypeConversionException must not call toString() on the body value");
            }
        };

        TypeConversionException exception = new TypeConversionException(body, String.class, new RuntimeException("cause"));

        assertThat(exception.getValue()).isSameAs(body);
        assertThat(exception.getMessage())
                .contains(body.getClass().getName())
                .contains("cause")
                .doesNotContain("with value");
    }
}
