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
package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@DisabledOnOs(WINDOWS)
public class ValidateAdvancedITCase extends JBangTestSupport {

    @BeforeEach
    public void installValidatePlugin() {
        execute("plugin add validate");
    }

    @Test
    public void testValidateProperties() {
        newFileInDataFolder("application.properties", "camel.main.name=MyApp\n");
        checkCommandOutputs(
                String.format("validate properties %s/application.properties", mountPoint()),
                "Validation success");
    }

    @Test
    public void testValidateSource() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        // validate source accepts any Camel source file including YAML routes
        checkCommandOutputs(
                String.format("validate source %s/route2.yaml", mountPoint()),
                "Validation success");
    }

    @Test
    public void testValidateNormalize() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        // normalize rewrites YAML routes into canonical form; output contains the normalised route
        checkCommandOutputs(
                String.format("validate normalize %s/route2.yaml", mountPoint()),
                "timer");
    }
}
