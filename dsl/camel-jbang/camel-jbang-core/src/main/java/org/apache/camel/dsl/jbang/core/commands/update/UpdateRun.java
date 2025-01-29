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
package org.apache.camel.dsl.jbang.core.commands.update;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.main.download.DownloadException;
import org.apache.camel.main.download.MavenDependencyDownloader;
import picocli.CommandLine;

/**
 * Command to update a Camel project to a specified version. This command supports updating projects for different
 * runtimes such as Camel Main, Spring Boot, and Quarkus. It uses Maven and OpenRewrite to apply the necessary updates.
 */
@CommandLine.Command(name = "run",
                     description = "Update Camel project")
public class UpdateRun extends CamelCommand {

    @CommandLine.Mixin
    private CamelUpdateMixin updateMixin;

    public UpdateRun(CamelJBangMain main) {
        super(main);
    }

    /**
     * Executes the update command for a Camel project. This method determines the appropriate Maven command based on
     * the runtime type (Camel Main, Spring Boot, or Quarkus) and executes it to update the project to the specified
     * version.
     *
     * @return           the exit code of the update process (0 for success, -1 for failure)
     * @throws Exception if an error occurs during the update process
     */
    @Override
    public Integer doCall() throws Exception {
        // Check if the current directory contains a Maven project (i.e., a pom.xml file)
        if (Files.list(Path.of("."))
                .noneMatch(f -> f.getFileName().toString().equals("pom.xml"))) {
            printer().println("No Maven Project found in the current directory, " +
                              "please execute camel upgrade run command in the directory containing the Maven project to update");

            return -1;
        }

        List<String> command = new ArrayList<>();
        try (MavenDependencyDownloader downloader = new MavenDependencyDownloader();) {
            downloader.setRepositories(updateMixin.repos);
            downloader.start();

            Update update = null;
            try {
                if (updateMixin.runtime == RuntimeType.quarkus) {
                    update = new CamelQuarkusUpdate(updateMixin, downloader);

                    command = update.command();
                } else {
                    update = new CamelUpdate(updateMixin, downloader);

                    command = update.command();
                }
            } catch (CamelUpdateException ex) {
                printer().println(ex.getMessage());

                return -1;
            } catch (DownloadException e) {
                printer().println(String.format("Cannot find Camel Upgrade Recipes %s:%s:%s",
                        "org.apache.camel.upgrade", update.getArtifactCoordinates(), updateMixin.version));

                return -1;
            }
        }

        executeCommand(command);

        return 0;
    }

    /**
     * Executes a shell command and prints its output.
     *
     * @param  command              the command to execute
     * @return                      the exit code of the command execution
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the command execution is interrupted
     */
    private int executeCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.redirectErrorStream(true)
                .start();

        try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
             BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {

            String line;
            while ((line = stdInput.readLine()) != null || (line = stdError.readLine()) != null) {
                printer().println(line);
            }

            if (!p.waitFor(updateMixin.upgradeTimeout, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                printer().println("Update execution timed out");

                return -1;
            }

            int exitCode = p.exitValue();

            return exitCode;

        }
    }
}
