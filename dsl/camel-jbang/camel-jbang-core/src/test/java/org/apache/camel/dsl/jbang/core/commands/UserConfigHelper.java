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

package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;

public final class UserConfigHelper {

    private UserConfigHelper() {
        // prevent instantiation of utility class
    }

    public static void createUserConfig(String content) throws IOException {
        CommandLineHelper.useHomeDir("target");
        Path userConfigDir = Paths.get("target");
        if (!userConfigDir.toFile().exists()) {
            userConfigDir.toFile().mkdirs();
        }

        Files.writeString(userConfigDir.resolve(CommandLineHelper.USER_CONFIG), content,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }
}
