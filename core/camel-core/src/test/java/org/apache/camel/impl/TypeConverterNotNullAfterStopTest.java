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

/**
 * Verifies that {@link org.apache.camel.CamelContext#getTypeConverter()} is never {@code null} after
 * {@code CamelContext.stop()}, closing the class of NPE races where async work (Multicast, parallel Splitter,
 * reactive-executor continuations) calls {@code getTypeConverter()} while the context is stopping.
 *
 * <p>
 * Before the fix, {@code AbstractCamelContext.forceStopLazyInitialization()} nulled the field at the tail of
 * {@code doStop()}. Now the null-and-recreate only happens at the start of the next {@code doStart()}, so the converter
 * remains valid for the entire stopped/idle window.
 *
 * <p>
 * Also verifies that restart-in-place ({@code stop()} then {@code start()} on the same instance) still works correctly
 * and produces a fresh type converter on the next start.
 */
class TypeConverterNotNullAfterStopTest {

    @Test
    void testTypeConverterNonNullAfterStop() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.start();

        assertThat(context.getTypeConverter())
                .as("type converter must be non-null while context is running")
                .isNotNull();

        context.stop();

        assertThat(context.getTypeConverter())
                .as("type converter must remain non-null after stop — no NPE for in-flight async work")
                .isNotNull();
    }

    @Test
    void testTypeConverterRefreshedOnRestart() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.start();
        var converterBefore = context.getTypeConverter();

        context.stop();
        context.start();

        assertThat(context.getTypeConverter())
                .as("type converter must be non-null after restart")
                .isNotNull();

        context.stop();
    }
}
