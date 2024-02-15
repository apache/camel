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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.dsl.jbang.core.common.MavenGavComparator;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.tooling.maven.MavenGav;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "Displays all Camel dependencies required to run")
public class DependencyList extends CamelCommand {

    // ignored list of dependencies, can be either groupId or artifactId
    private static final String[] SKIP_DEPS = new String[] { "org.fusesource.jansi", "org.apache.logging.log4j" };

    @CommandLine.Parameters(description = "The Camel file(s) to inspect for dependencies.", arity = "0..9",
                            paramLabel = "<files>")
    protected Path[] filePaths;

    @CommandLine.Option(names = { "--output" }, description = "Output format (gav, maven, jbang)", defaultValue = "gav")
    protected String output;

    @CommandLine.Option(names = { "-v" }, description = "Print additional verbose output.")
    protected boolean verbose = false;

    private Set<MavenGav> dependencies;

    public DependencyList(CamelJBangMain main) {
        super(main);
        Arrays.sort(SKIP_DEPS);
    }

    @Override
    public Integer doCall() throws Exception {
        if (!"gav".equals(output) && !"maven".equals(output) && !"jbang".equals(output)) {
            System.err.println("--output must be either gav, maven or jbang, was: " + output);
            return 1;
        }
        calculate();
        return 0;
    }

    public void calculate() throws Exception {
        List<String> routeFiles = new ArrayList<>();
        for (Path filePath : filePaths) {
            routeFiles.add("file://" + filePath.toAbsolutePath());
        }
        final KameletMain main = new KameletMain();
        main.setDownload(false);
        main.setFresh(false);
        RunDownloadListener downloadListener = new RunDownloadListener();
        main.setDownloadListener(downloadListener);
        main.setSilent(!verbose);
        // enable stub in silent mode so we do not use real components
        main.setStubPattern("*");
        // do not run for very long in silent run
        main.addInitialProperty("camel.main.autoStartup", "false");
        main.addInitialProperty("camel.main.durationMaxSeconds", "1");
        main.addInitialProperty("camel.jbang.verbose", Boolean.toString(verbose));
        main.addInitialProperty("camel.main.routesIncludePattern", String.join(",", routeFiles));

        main.start();
        main.run();

        dependencies = downloadListener.getDependencies();
        int total = dependencies.size();
        int i = 0;
        for (MavenGav gav : dependencies) {
            outputGav(gav, i++, total);
        }
        main.stop();
        main.shutdown();
    }

    public Set<MavenGav> getDependencies() {
        return dependencies;
    }

    protected void outputGav(MavenGav gav, int index, int total) {
        if ("gav".equals(output)) {
            printer().println(String.valueOf(gav));
        } else if ("maven".equals(output)) {
            printer().println("<dependency>");
            printer().printf("    <groupId>%s</groupId>%n", gav.getGroupId());
            printer().printf("    <artifactId>%s</artifactId>%n", gav.getArtifactId());
            printer().printf("    <version>%s</version>%n", gav.getVersion());
            printer().println("</dependency>");
        } else if ("jbang".equals(output)) {
            if (index == 0) {
                printer().println("//DEPS org.apache.camel:camel-bom:" + gav.getVersion() + "@pom");
            }
            if (gav.getGroupId().equals("org.apache.camel")) {
                // jbang has version in @pom so we should remove this
                gav.setVersion(null);
            }
            printer().println("//DEPS " + gav);
        }
    }

    private static class RunDownloadListener implements DownloadListener {

        final Set<MavenGav> dependencies = new TreeSet<>(new MavenGavComparator());

        @Override
        public void onDownloadDependency(String groupId, String artifactId, String version) {
            MavenGav gav = MavenGav.fromCoordinates(groupId, artifactId, version, null, null);
            if (!skipArtifact(groupId, artifactId)) {
                dependencies.add(gav);
            }
        }

        private boolean skipArtifact(String groupId, String artifactId) {
            return Arrays.binarySearch(SKIP_DEPS, artifactId) >= 0 || Arrays.binarySearch(SKIP_DEPS, groupId) >= 0;
        }

        @Override
        public void onAlreadyDownloadedDependency(String groupId, String artifactId, String version) {
        }

        private Set<MavenGav> getDependencies() {
            return dependencies;
        }
    }
}
