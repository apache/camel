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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.IOHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "wrapper", description = "Install Camel wrapper scripts for version pinning", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel wrapper",
                 "  camel wrapper --camel-version=4.10.0" })
public class WrapperCommand extends CamelCommand {

    private static final String DEFAULT_REPO_URL = "https://repo1.maven.org/maven2";
    private static final String WRAPPER_PROPERTIES_FILE = "camel-wrapper.properties";
    private static final String CAMEL_DIR = ".camel";

    @CommandLine.Option(names = { "--camel-version" },
                        description = "Camel version to pin (defaults to current version)")
    String camelVersion;

    @CommandLine.Option(names = { "--repo-url" },
                        description = "Maven repository URL for downloading the launcher",
                        defaultValue = DEFAULT_REPO_URL)
    String repoUrl = DEFAULT_REPO_URL;

    @CommandLine.Option(names = { "--dir", "--directory" },
                        description = "Directory where wrapper files will be created",
                        defaultValue = ".")
    String directory = ".";

    public WrapperCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (camelVersion == null || camelVersion.isEmpty()) {
            CamelCatalog catalog = new DefaultCamelCatalog();
            camelVersion = catalog.getCatalogVersion();
        }

        Path baseDir = Paths.get(directory).toAbsolutePath().normalize();
        Path camelDir = baseDir.resolve(CAMEL_DIR);

        // Create .camel directory
        Files.createDirectories(camelDir);

        // Write camel-wrapper.properties
        writeProperties(camelDir);

        // Write camelw script
        writeScript(baseDir, "camelw");

        // Write camelw.cmd script
        writeScript(baseDir, "camelw.cmd");

        printer().println("Apache Camel wrapper installed successfully.");
        printer().println("  Camel version: " + camelVersion);
        printer().println("  Properties: " + camelDir.resolve(WRAPPER_PROPERTIES_FILE));
        printer().println("  Unix script: " + baseDir.resolve("camelw"));
        printer().println("  Windows script: " + baseDir.resolve("camelw.cmd"));
        printer().println();
        printer().println("You can now use ./camelw instead of camel to run with the pinned version.");

        return 0;
    }

    void writeProperties(Path camelDir) throws IOException {
        String distributionUrl = buildDistributionUrl();
        String content = """
                # Licensed to the Apache Software Foundation (ASF) under one
                # or more contributor license agreements.  See the NOTICE file
                # distributed with this work for additional information
                # regarding copyright ownership.  The ASF licenses this file
                # to you under the Apache License, Version 2.0 (the
                # "License"); you may not use this file except in compliance
                # with the License.  You may obtain a copy of the License at
                #
                #   http://www.apache.org/licenses/LICENSE-2.0
                #
                # Unless required by applicable law or agreed to in writing,
                # software distributed under the License is distributed on an
                # "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
                # KIND, either express or implied.  See the License for the
                # specific language governing permissions and limitations
                # under the License.
                camel.version=%s
                distributionUrl=%s
                """.formatted(camelVersion, distributionUrl);

        Files.writeString(camelDir.resolve(WRAPPER_PROPERTIES_FILE), content);
    }

    String buildDistributionUrl() {
        return repoUrl
               + "/org/apache/camel/camel-launcher/"
               + camelVersion
               + "/camel-launcher-"
               + camelVersion
               + ".jar";
    }

    void writeScript(Path baseDir, String scriptName) throws IOException {
        String resourcePath = "camel-wrapper/" + scriptName;
        try (InputStream is = WrapperCommand.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            String content = IOHelper.loadText(is);
            Path scriptPath = baseDir.resolve(scriptName);
            Files.writeString(scriptPath, content);

            // Make Unix script executable
            if (!scriptName.endsWith(".cmd")) {
                makeExecutable(scriptPath);
            }
        }
    }

    private void makeExecutable(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException e) {
            // Windows or other OS that doesn't support POSIX permissions
        }
    }
}
