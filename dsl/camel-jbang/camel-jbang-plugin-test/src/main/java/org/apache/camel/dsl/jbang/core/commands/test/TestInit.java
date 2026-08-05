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

package org.apache.camel.dsl.jbang.core.commands.test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.camel.catalog.VersionHelper;
import org.apache.camel.dsl.jbang.core.commands.ExportHelper;
import org.apache.camel.util.IOHelper;
import org.citrusframework.CitrusSettings;
import org.citrusframework.CitrusVersion;
import org.citrusframework.jbang.cli.CitrusJBangMain;
import org.citrusframework.jbang.cli.commands.Init;
import picocli.CommandLine;

/**
 * Automatically uses test subfolder as a working directory for creating new tests. Automatically adds a
 * citrus-application.properties configuration if not present.
 */
@CommandLine.Command(name = "init", description = "Creates a new Citrus test")
public class TestInit extends Init {

    public TestInit(CitrusJBangMain citrus) {
        super(citrus);
    }

    @Override
    protected String resolveTargetDirectory(String directory) {
        if (directory == null || ".".equals(directory)) {
            return TestPlugin.TEST_DIR;
        }

        return super.resolveTargetDirectory(directory);
    }

    @Override
    protected void initAdditionalFiles(Path workingDir) {
        // Create Citrus application properties if not present
        if (!workingDir.resolve(CitrusSettings.getApplicationPropertiesFile()).toFile().exists()) {
            Path citrusApplicationProperties = workingDir.resolve(CitrusSettings.getApplicationPropertiesFile());
            try (InputStream is
                    = TestPlugin.class.getClassLoader()
                            .getResourceAsStream("templates/citrus-application-properties.tmpl")) {
                String context = IOHelper.loadText(is);

                context = context.replaceAll("\\{\\{ \\.CitrusVersion }}", CitrusVersion.version());
                context = context.replaceAll("\\{\\{ \\.CamelVersion }}", new VersionHelper().getVersion());

                ExportHelper.safeCopy(new ByteArrayInputStream(context.getBytes(StandardCharsets.UTF_8)),
                        citrusApplicationProperties);
            } catch (Exception e) {
                printer().println("Error: Failed to create '%s' in: %s"
                        .formatted(CitrusSettings.getApplicationPropertiesFile(), citrusApplicationProperties));
            }
        }
    }
}
