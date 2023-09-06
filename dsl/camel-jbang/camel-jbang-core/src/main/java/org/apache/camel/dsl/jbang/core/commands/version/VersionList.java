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

import java.io.LineNumberReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
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
import org.apache.camel.tooling.maven.RepositoryResolver;
import org.apache.camel.tooling.model.ReleaseModel;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "Displays available Camel versions",
                     sortOptions = false)
public class VersionList extends CamelCommand {

    private static final String YYYY_MM_DD = "yyyy-MM-dd";

    private static final String GIT_CAMEL_URL
            = "https://raw.githubusercontent.com/apache/camel-website/main/content/releases/release-%s.md";
    private static final String GIT_CAMEL_QUARKUS_URL
            = "https://raw.githubusercontent.com/apache/camel-website/main/content/releases/q/release-%s.md";

    @CommandLine.Option(names = { "--runtime" }, completionCandidates = RuntimeCompletionCandidates.class,
                        description = "Runtime (spring-boot, quarkus, or camel-main)")
    String runtime;

    @CommandLine.Option(names = { "--from-version" },
                        description = "Filter by Camel version (inclusive)", defaultValue = "3.14.0")
    String fromVersion = "3.14.0";

    @CommandLine.Option(names = { "--to-version" },
                        description = "Filter by Camel version (exclusive)")
    String toVersion;

    @CommandLine.Option(names = { "--repo" }, description = "Maven repository for downloading available versions")
    String repo;

    @CommandLine.Option(names = { "--lts" }, description = "Only show LTS supported releases")
    boolean lts;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    boolean fresh;

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by (version, or date)", defaultValue = "version")
    String sort;

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

            RepositoryResolver rr = main.getCamelContext().hasService(RepositoryResolver.class);
            if (rr != null) {
                repo = rr.resolveRepository(repo);
            }

            // ensure from and to-version have major.minor
            if (fromVersion != null) {
                if (!(fromVersion.contains(".") || fromVersion.contains(","))) {
                    fromVersion = fromVersion + ".0";
                }
            }
            if (toVersion != null) {
                if (!(toVersion.contains(".") || toVersion.contains(","))) {
                    toVersion = toVersion + ".0";
                }
            }

            versions = downloader.resolveAvailableVersions(g, a, fromVersion, repo);
            versions = versions.stream().filter(v -> acceptVersion(v[0])).collect(Collectors.toList());

            main.stop();
        } catch (Exception e) {
            e.printStackTrace();
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
            if (rm == null) {
                // unknown release but if it's an Apache Camel release we can grab from online
                int dots = StringHelper.countChar(v[0], '.');
                if (dots == 2) {
                    rm = onlineRelease(runtime, row.coreVersion);
                }
            }
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
            case "date":
                String d1 = o1.releaseDate != null ? o1.releaseDate : "";
                String d2 = o2.releaseDate != null ? o2.releaseDate : "";
                return d1.compareTo(d2) * negate;
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
        if (fromVersion != null && toVersion != null) {
            return VersionHelper.isBetween(version, fromVersion, toVersion);
        }
        return VersionHelper.isGE(version, fromVersion);
    }

    private ReleaseModel onlineRelease(String runtime, String coreVersion) throws Exception {
        String gitUrl = String.format("quarkus".equals(runtime) ? GIT_CAMEL_QUARKUS_URL : GIT_CAMEL_URL, coreVersion);

        HttpClient hc = HttpClient.newHttpClient();
        HttpResponse<String> res = hc.send(HttpRequest.newBuilder(new URI(gitUrl)).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 200) {
            ReleaseModel model = new ReleaseModel();
            LineNumberReader lr = new LineNumberReader(new StringReader(res.body()));
            String line = lr.readLine();
            while (line != null) {
                if (line.startsWith("date:")) {
                    model.setDate(line.substring(5).trim());
                } else if (line.startsWith("version:")) {
                    model.setVersion(line.substring(8).trim());
                } else if (line.startsWith("eol:")) {
                    model.setEol(line.substring(4).trim());
                } else if (line.startsWith("kind:")) {
                    model.setKind(line.substring(5).trim());
                } else if (line.startsWith("jdk:")) {
                    String s = line.substring(4).trim();
                    if (s.startsWith("[") && s.endsWith("]")) {
                        s = s.substring(1, s.length() - 1);
                    }
                    model.setJdk(s);
                }
                line = lr.readLine();
            }
            if (model.getVersion() != null) {
                return model;
            }
        }

        return null;
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
