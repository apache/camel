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
package org.apache.camel.main;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainAddDynamicRouteTest {

    @BindToRegistry("lines")
    private final List<String> lines = new ArrayList<>();

    @Test
    public void addDynamicRoute() throws Exception {
        final Main main = new Main();
        main.addInitialProperty("prop", "value");
        main.configure().setDurationMaxMessages(2);

        try (MainConfigurationProperties conf = main.configure()) {
            conf.addRoutesBuilder(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("timer:one?repeatCount=1")
                            .setBody().simple("{{prop}}").bean(lines, "add")
                            .process(e -> e.getContext().addRoutes(new RouteBuilder() {
                                @Override
                                public void configure() throws Exception {
                                    from("timer:two?repeatCount=1")
                                            .setBody().simple("{{prop}}").bean(lines, "add");
                                }
                            }));
                }
            });

            main.run();

            Assertions.assertEquals(2, lines.size());
            Assertions.assertEquals("value", lines.get(0));
            Assertions.assertEquals("value", lines.get(1));
        }
    }
}
