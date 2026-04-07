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
package org.apache.camel.dsl.jbang.core.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.main.KameletMain;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class PluginRunCustomizerTest {

    @Test
    public void testPluginDefaultReturnsEmpty() {
        Plugin plugin = (commandLine, main) -> {
            // no-op
        };

        assertTrue(plugin.getRunCustomizer().isEmpty());
    }

    @Test
    public void testPluginWithRunCustomizer() {
        List<String> capturedFiles = new ArrayList<>();
        boolean[] called = { false };

        PluginRunCustomizer customizer = (main, files) -> {
            called[0] = true;
            capturedFiles.addAll(files);
        };

        Plugin plugin = new Plugin() {
            @Override
            public void customize(CommandLine commandLine, CamelJBangMain main) {
                // no-op
            }

            @Override
            public Optional<PluginRunCustomizer> getRunCustomizer() {
                return Optional.of(customizer);
            }
        };

        Optional<PluginRunCustomizer> result = plugin.getRunCustomizer();
        assertTrue(result.isPresent());

        // Simulate invoking the customizer
        List<String> testFiles = List.of("route.yaml", "beans.xml");
        result.get().beforeRun(new KameletMain("test"), testFiles);

        assertTrue(called[0]);
        assertEquals(testFiles, capturedFiles);
    }
}
