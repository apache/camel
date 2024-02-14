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
package org.apache.camel.main.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.util.IOHelper;

/**
 * When running we need to store
 */
public final class CamelJBangSettingsHelper {

    private static final String WORK_DIR = ".camel-jbang";
    private static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";
    private static final File FILE = new File(WORK_DIR + "/" + RUN_SETTINGS_FILE);

    private CamelJBangSettingsHelper() {
    }

    public static void writeSettingsIfNotExists(String key, String value) {
        if (FILE.exists()) {
            try {

                String context;
                try (FileInputStream fis = new FileInputStream(FILE)) {
                    context = IOHelper.loadText(fis);
                }

                if (!context.contains(key + "=")) {
                    // append line as key has not been set before
                    String line = key + "=" + value;
                    try (FileOutputStream fos = new FileOutputStream(FILE, true)) {
                        fos.write(line.getBytes(StandardCharsets.UTF_8));
                        fos.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

}
