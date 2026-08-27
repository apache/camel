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
package org.apache.camel.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that after {@code CamelContext.stop()} the type converter is replaced by a sentinel that throws a
 * descriptive {@link IllegalStateException} instead of leaving {@code null} and causing a bare
 * {@link NullPointerException} at the ~200 unguarded {@code getTypeConverter()} call sites across {@code core/}.
 *
 * <p>
 * Real-world trigger: a Quartz SFTP poll, Multicast thread, or script processor calls
 * {@code exchange.getIn(SomeType.class)}, {@code exchange.getMessage(SomeType.class)}, or any other
 * {@code context.getTypeConverter().convertTo(…)} while the CamelContext is shutting down. The sentinel installed by
 * {@code DefaultCamelContextExtension.resetTypeConverter()} fixes every call site at once without scattering
 * per-call-site null-guards.
 */
class StoppedTypeConverterTest {

    @Test
    void testTypeConverterAfterStopIsNonNullSentinel() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.start();

        assertThat(context.getTypeConverter())
                .as("type converter must be non-null while context is running")
                .isNotNull();

        context.stop();

        assertThat(context.getTypeConverter())
                .as("type converter must be non-null even after stop — sentinel, not null")
                .isNotNull();
    }

    @Test
    void testTypeConverterAfterStopThrowsIllegalStateException() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.start();
        context.stop();

        assertThatThrownBy(() -> context.getTypeConverter().convertTo(String.class, 42))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CamelContext has been stopped");
    }
}
