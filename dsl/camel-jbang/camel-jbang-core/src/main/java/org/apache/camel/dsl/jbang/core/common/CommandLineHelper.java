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

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.camel.util.IOHelper;
import picocli.CommandLine;

/**
 * Helper for CLI command line.
 */
public class CommandLineHelper {

    public static final String USER_CONFIG = ".camel-jbang-user.properties";

    public static void augmentWithUserConfiguration(CommandLine commandLine, String... args) {
        File file = new File(System.getProperty("user.home"), USER_CONFIG);
        if (file.isFile() && file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                Properties prop = new Properties();
                prop.load(fis);
                IOHelper.close(fis);
                if (!prop.isEmpty()) {
                    commandLine.setDefaultValueProvider(new CommandLine.PropertiesDefaultProvider(prop));
                }
            } catch (Exception e) {
                throw new RuntimeException("Cannot load user configuration: " + file);
            } finally {
                IOHelper.close(fis);
            }
        }
    }

}
