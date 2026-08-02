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
package org.apache.camel.component.kamelet;

import java.lang.reflect.Field;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.util.concurrent.ThreadType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24320: Kamelet route creation must not NPE when virtual threads are enabled on JDK 25+.
 * <p>
 * Regression guard for {@link ContextValueFactory.ScopedValueContextValue#orElse(Object)} when the fallback is
 * {@code null} (see CAMEL-24320). ScopedValue unit coverage lives in the integration path because MRJ classes are
 * packaged under {@code META-INF/versions/25}.
 */
@EnabledForJreRange(min = JRE.JAVA_25)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class KameletVirtualThreadsRouteCreationTest {

    private static final String VIRTUAL_THREADS_PROPERTY = "camel.threads.virtual.enabled";

    private String previousVirtualThreadsProperty;

    @BeforeEach
    void enableVirtualThreads() throws Exception {
        previousVirtualThreadsProperty = System.getProperty(VIRTUAL_THREADS_PROPERTY);
        System.setProperty(VIRTUAL_THREADS_PROPERTY, "true");
        resetThreadTypeField();
    }

    @AfterEach
    void restoreVirtualThreadsProperty() throws Exception {
        if (previousVirtualThreadsProperty == null) {
            System.clearProperty(VIRTUAL_THREADS_PROPERTY);
        } else {
            System.setProperty(VIRTUAL_THREADS_PROPERTY, previousVirtualThreadsProperty);
        }
        resetThreadTypeField();
    }

    @Test
    void mainStartsKameletRouteWithVirtualThreadsEnabled() {
        Main main = new Main();
        main.configure().withVirtualThreadsEnabled(true).addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("kamelet:vt-repro-source").routeId("vt-kamelet-repro").to("mock:vt-out");
            }
        });
        main.start();
        try {
            assertThat(main.getCamelContext().getRoute("vt-kamelet-repro")).isNotNull();
        } finally {
            main.stop();
        }
    }

    private static void resetThreadTypeField() throws Exception {
        Field field = ThreadType.class.getDeclaredField("current");
        field.setAccessible(true);
        field.set(null, null);
    }
}
