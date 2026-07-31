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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CAMEL-24320: Kamelet route creation must not NPE when virtual threads are enabled on JDK 25+.
 * <p>
 * Virtual threads must be selected before the first {@code ContextValue} is created; the system property is set in a
 * static initializer so {@code ContextValueFactory} picks the ScopedValue backend when this class loads first in an
 * isolated fork. {@link org.apache.camel.util.concurrent.ScopedValueContextValueOrElseTest} is the primary unit guard.
 */
@Isolated
@EnabledForJreRange(min = JRE.JAVA_25)
class KameletVirtualThreadsRouteCreationTest {

    static {
        System.setProperty("camel.threads.virtual.enabled", "true");
    }

    @AfterEach
    void resetThreadType() throws Exception {
        Field field = ThreadType.class.getDeclaredField("current");
        field.setAccessible(true);
        field.set(null, null);
        System.clearProperty("camel.threads.virtual.enabled");
    }

    @Test
    void mainStartsKameletRouteWithVirtualThreadsEnabled() {
        assertThatCode(() -> {
            Main main = new Main();
            main.configure().withVirtualThreadsEnabled(true).addRoutesBuilder(new RouteBuilder() {
                @Override
                public void configure() {
                    from("kamelet:vt-repro-source").routeId("vt-kamelet-repro").to("mock:vt-out");
                }
            });
            main.start();
            assertThat(main.getCamelContext().getRoute("vt-kamelet-repro")).isNotNull();
            main.stop();
        }).doesNotThrowAnyException();
    }
}
