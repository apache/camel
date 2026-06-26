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
package org.apache.camel.dsl.jbang.core.commands.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;

public class BaseConfigTestSupport extends CamelCommandBaseTestSupport {

    @AfterEach
    void removeConfigFiles() throws IOException {
        Files.deleteIfExists(Paths.get(CommandLineHelper.LOCAL_USER_CONFIG));
        // remove the global config file written under target by UserConfigHelper.createUserConfig, so it does not
        // leak into later tests in the same JVM. Target the build directory explicitly to never touch the real home.
        Files.deleteIfExists(Paths.get("target", CommandLineHelper.USER_CONFIG));
    }
}
