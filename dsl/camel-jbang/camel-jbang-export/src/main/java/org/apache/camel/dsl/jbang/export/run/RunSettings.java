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
package org.apache.camel.dsl.jbang.export.run;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.function.Supplier;

import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.main.KameletMain;

/**
 * The run settings: the file (camel-jbang-run.properties in the work directory) where a run writes what it is
 * configured with, and its dependencies, which an export reads.
 *
 * @since 4.24
 */
public class RunSettings {

    private final Path workDir;

    public RunSettings(Path workDir) {
        this.workDir = workDir;
    }

    /**
     * The run settings file.
     */
    public Path getFile() {
        return workDir.resolve(ExportHelper.RUN_SETTINGS_FILE);
    }

    /**
     * Writes the setting to the file.
     */
    public void write(String key, String value) {
        try {
            // use java.util.Properties to ensure the value is escaped correctly
            Properties prop = new Properties();
            prop.setProperty(key, value);
            StringWriter sw = new StringWriter();
            prop.store(sw, null);

            Path runSettingsPath = getFile();

            StringBuilder content = new StringBuilder();
            String[] lines = sw.toString().split(System.lineSeparator());
            for (String line : lines) {
                // properties store timestamp as comment which we want to skip
                if (!line.startsWith("#")) {
                    content.append(line).append(System.lineSeparator());
                }
            }

            // Append to the file if it exists, otherwise create it
            if (Files.exists(runSettingsPath)) {
                Files.write(runSettingsPath, content.toString().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.APPEND);
            } else {
                Files.write(runSettingsPath, content.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Sets the initial property of Camel Main and writes the setting: the value of the profile properties when it has
     * the key, else the given value (none when null).
     */
    public void write(KameletMain main, Properties existing, String key, String value) {
        String val = existing != null ? existing.getProperty(key, value) : value;
        if (val != null) {
            main.addInitialProperty(key, val);
            write(key, val);
        }
    }

    /**
     * Sets the property of Camel Main and writes the setting, for an option that is given on the command line or else
     * comes from the profile properties.
     *
     * @param value the value of the option, null when it is not given
     */
    public void write(KameletMain main, Properties existing, String key, Supplier<String> value) {
        // a flag given on the command line (the supplier answers non-null only then) wins over the profile
        // properties file; the file is the default when the flag is not given (CAMEL-24705)
        String val = value.get();
        if (val != null) {
            // an explicit flag: an override property, as camel-main loads application.properties itself on top of
            // the initial properties and the file would win otherwise
            main.addOverrideProperty(key, val);
            write(key, val);
            return;
        }
        if (existing != null) {
            val = existing.getProperty(key);
        }
        if (val != null) {
            main.addInitialProperty(key, val);
            write(key, val);
        }
    }
}
