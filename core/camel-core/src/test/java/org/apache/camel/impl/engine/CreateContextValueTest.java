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

import java.lang.reflect.Field;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.concurrent.ThreadType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24320: {@link ExtendedCamelContext#getCreateRoute()} and {@link ExtendedCamelContext#getCreateProcessor()} must
 * return null outside a binding scope (used by Kamelet endpoint init).
 */
class CreateContextValueTest {

    private static final String VIRTUAL_THREADS_PROPERTY = "camel.threads.virtual.enabled";

    private String previousVirtualThreadsProperty;

    @Test
    void getCreateRouteReturnsNullOutsideScope() {
        ExtendedCamelContext extension = new DefaultCamelContext().getCamelContextExtension();

        assertThat(extension.getCreateRoute()).isNull();
    }

    @Test
    void getCreateProcessorReturnsNullOutsideScope() {
        ExtendedCamelContext extension = new DefaultCamelContext().getCamelContextExtension();

        assertThat(extension.getCreateProcessor()).isNull();
    }

    @EnabledForJreRange(min = JRE.JAVA_25)
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    @Test
    void getCreateRouteDoesNotThrowWithVirtualThreadsEnabled() throws Exception {
        enableVirtualThreads();
        try {
            ExtendedCamelContext extension = new DefaultCamelContext().getCamelContextExtension();

            assertThat(extension.getCreateRoute()).isNull();
            assertThat(extension.getCreateProcessor()).isNull();
        } finally {
            restoreVirtualThreadsProperty();
        }
    }

    private void enableVirtualThreads() throws Exception {
        previousVirtualThreadsProperty = System.getProperty(VIRTUAL_THREADS_PROPERTY);
        System.setProperty(VIRTUAL_THREADS_PROPERTY, "true");
        resetThreadTypeField();
    }

    private void restoreVirtualThreadsProperty() throws Exception {
        if (previousVirtualThreadsProperty == null) {
            System.clearProperty(VIRTUAL_THREADS_PROPERTY);
        } else {
            System.setProperty(VIRTUAL_THREADS_PROPERTY, previousVirtualThreadsProperty);
        }
        resetThreadTypeField();
    }

    private static void resetThreadTypeField() throws Exception {
        Field field = ThreadType.class.getDeclaredField("current");
        field.setAccessible(true);
        field.set(null, null);
    }
}
