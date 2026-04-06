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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DiagramScreenshotterTest {

    @Test
    void constructorStoresArguments() {
        DiagramScreenshotter screenshotter = new DiagramScreenshotter(
                "http://localhost:8888/hawtio",
                "http://127.0.0.1:8778/jolokia",
                Path.of("output.png"),
                "myRoute",
                "/usr/bin/chromium",
                60);
        assertNotNull(screenshotter);
    }

    @Test
    void constructorAcceptsNullRouteId() {
        DiagramScreenshotter screenshotter = new DiagramScreenshotter(
                "http://localhost:8888/hawtio",
                "http://127.0.0.1:8778/jolokia",
                Path.of("output.png"),
                null,
                "/usr/bin/chromium",
                30);
        assertNotNull(screenshotter);
    }
}
