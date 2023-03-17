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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.model.ReleaseModel;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "Displays available Camel versions")
public class VersionList extends CamelCommand {

    private static final String YYYY_MM_DD = "yyyy-MM-dd";

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by version", defaultValue = "version")
    String sort;

    @CommandLine.Option(names = { "--runtime" }, completionCandidates = RuntimeCompletionCandidates.class,
                        description = "Runtime (spring-boot, quarkus, or camel-main)")
    String runtime;

    @CommandLine.Option(names = { "--minimum-version" },
                        description = "Minimum Camel version to avoid resolving too old releases", defaultValue = "3.14.0")
    String minimumVersion = "3.14.0";

    @CommandLine.Option(names = { "--repo" }, description = "Maven repository for downloading available versions")
    String repo;

    @CommandLine.Option(names = { "--lts" }, description = "Only show LTS supported releases")
    boolean lts;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    boolean fresh;

    public VersionList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
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
                a = "camel-spring-boot";
            } else if ("quarkus".equalsIgnoreCase(runtime)) {
                g = "org.apache.camel.quarkus";
                a = "camel-quarkus-catalog";
            }

            versions = downloader.resolveAvailableVersions(g, a, minimumVersion, repo);
            versions = versions.stream().filter(v -> acceptVersion(v[0])).collect(Collectors.toList());

            main.stop();
        } catch (Exception e) {
            System.out.println("Error downloading available Camel versions");
            return 1;
        }

        CamelCatalog catalog = new DefaultCamelCatalog();
        List<ReleaseModel> releases = "quarkus".equals(runtime) ? catalog.camelQuarkusReleases() : catalog.camelReleases();

        List<Row> rows = new ArrayList<>();
        for (String[] v : versions) {
            Row row = new Row();
            rows.add(row);
            row.coreVersion = v[0];
            row.runtimeVersion = v[1];

            // enrich with details from catalog (if we can find any)
            String catalogVersion = "quarkus".equals(runtime) ? v[1] : v[0];
            ReleaseModel rm = releases.stream().filter(r -> catalogVersion.equals(r.getVersion())).findFirst().orElse(null);
            if (rm != null) {
                row.releaseDate = rm.getDate();
                row.eolDate = rm.getEol();
                row.jdks = rm.getJdk();
                row.kind = rm.getKind();
            }
        }

        if (lts) {
            rows.removeIf(r -> !"lts".equalsIgnoreCase(r.kind));
        }

        // sort rows
        rows.sort(this::sortRow);

        // camel-quarkus is not LTS and have its own release schedule
        System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("CAMEL VERSION")
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.coreVersion),
                new Column().header("QUARKUS").visible("quarkus".equalsIgnoreCase(runtime))
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.runtimeVersion),
                new Column().header("SPRING-BOOT").visible("spring-boot".equalsIgnoreCase(runtime))
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.runtimeVersion),
                new Column().header("JDK")
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT).with(this::jdkVersion),
                new Column().header("KIND")
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(this::kind),
                new Column().header("RELEASED")
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT).with(this::releaseDate),
                new Column().header("SUPPORTED UNTIL")
                        .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT).with(this::eolDate))));

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
                return VersionHelper.compare(o1.coreVersion, o2.coreVersion) * negate;
            default:
                return 0;
        }
    }

    private String jdkVersion(Row r) {
        return r.jdks;
    }

    private String kind(Row r) {
        if (r.kind != null) {
            return r.kind.toUpperCase(Locale.ROOT);
        }
        return "";
    }

    private String releaseDate(Row r) {
        try {
            if (r.releaseDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD);
                Date d = sdf.parse(r.releaseDate);
                SimpleDateFormat sdf2 = new SimpleDateFormat("MMMM yyyy", Locale.US);
                return sdf2.format(d);
            }
        } catch (Exception e) {
            // ignore
        }
        return r.releaseDate != null ? r.releaseDate : "";
    }

    private String eolDate(Row r) {
        try {
            if (r.eolDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD);
                Date d = sdf.parse(r.eolDate);
                SimpleDateFormat sdf2 = new SimpleDateFormat("MMMM yyyy", Locale.US);
                return sdf2.format(d);
            }
        } catch (Exception e) {
            // ignore
        }
        return r.eolDate != null ? r.eolDate : "";
    }

    private boolean acceptVersion(String version) {
        if (version == null) {
            return false;
        }
        return VersionHelper.isGE(version, minimumVersion);
    }

    private static class Row {
        String coreVersion;
        String runtimeVersion;
        String releaseDate;
        String eolDate;
        String kind;
        String jdks;
    }

}
