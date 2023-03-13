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
package org.apache.camel.dsl.jbang.core.commands.version;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.MavenDependencyDownloader;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "Displays available Camel versions")
public class VersionList extends CamelCommand {

    private static final String MINIMUM_VERSION = "3.14.0";

    // TODO: Filter for minimum camel version
    // TODO: grab Q and SB runtime version

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by version", defaultValue = "version")
    String sort;

    @CommandLine.Option(names = { "--runtime" }, completionCandidates = RuntimeCompletionCandidates.class,
                        description = "Runtime (spring-boot, quarkus, or camel-main)")
    String runtime;

    @CommandLine.Option(names = { "--repo", "--repos" }, description = "Maven repository for downloading available versions")
    String repo;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    boolean fresh;

    public VersionList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        configureLoggingOff();
        KameletMain main = new KameletMain();

        List<String[]> versions;
        try {
            main.setFresh(fresh);
            main.setRepos(repo);
            main.start();

            // use kamelet-main to download from maven
            MavenDependencyDownloader downloader = main.getCamelContext().hasService(MavenDependencyDownloader.class);

            String g = "org.apache.camel";
            String a = "camel-catalog";
            if ("spring-boot".equalsIgnoreCase(runtime)) {
                g = "org.apache.camel.springboot";
                a = "camel-catalog-provider-springboot";
            } else if ("quarkus".equalsIgnoreCase(runtime)) {
                g = "org.apache.camel.quarkus";
                a = "camel-quarkus-catalog";
            }

            versions = downloader.resolveAvailableVersions(g, a, repo);
            versions = versions.stream().filter(v -> acceptVersion(v[0])).collect(Collectors.toList());

            main.stop();
        } catch (Exception e) {
            System.out.println("Error downloading available Camel versions");
            return 1;
        }

        List<Row> rows = new ArrayList<>();
        for (String[] v : versions) {
            Row row = new Row();
            rows.add(row);
            row.coreVersion = v[0];
            row.runtimeVersion = v[1];
        }

        // sort rows
        rows.sort(this::sortRow);

        System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("QUARKUS").visible("quarkus".equalsIgnoreCase(runtime))
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.runtimeVersion),
                new Column().header("SPRING-BOOT").visible("spring-boot".equalsIgnoreCase(runtime))
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.runtimeVersion),
                new Column().header("CAMEL VERSION")
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.coreVersion))));

        return 0;
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "version":
                String v1 = o1.runtimeVersion != null ? o1.runtimeVersion : o1.coreVersion;
                String v2 = o2.runtimeVersion != null ? o2.runtimeVersion : o2.coreVersion;
                return VersionHelper.compare(v1, v2) * negate;
            default:
                return 0;
        }
    }

    private boolean acceptVersion(String version) {
        if (version == null) {
            return false;
        }
        return VersionHelper.isGE(version, MINIMUM_VERSION);
    }

    private static class Row {
        String coreVersion;
        String runtimeVersion;
    }

}
