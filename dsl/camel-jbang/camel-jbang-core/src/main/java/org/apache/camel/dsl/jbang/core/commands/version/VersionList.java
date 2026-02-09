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

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.dsl.jbang.core.model.VersionListDTO;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.RepositoryResolver;
import org.apache.camel.tooling.model.ReleaseModel;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.CAMEL_INSTANCE_TYPE;

@CommandLine.Command(name = "list", description = "Displays available Camel versions",
                     sortOptions = false, showDefaultValues = true)
public class VersionList extends CamelCommand {

    private static final String VERSION_LIST_CHECKER = ".camel-jbang-version-list.json";

    private static final String YYYY_MM_DD = "yyyy-MM-dd";

    private static final String GIT_CAMEL_URL
            = "https://raw.githubusercontent.com/apache/camel-website/main/content/releases/release-%s.md";
    private static final String GIT_CAMEL_QUARKUS_URL
            = "https://raw.githubusercontent.com/apache/camel-website/main/content/releases/q/release-%s.md";

    private static final String DEFAULT_DATE_FORMAT = "MMMM yyyy";

    @CommandLine.Option(names = { "--runtime" },
                        defaultValue = "camel-main",
                        completionCandidates = RuntimeCompletionCandidates.class,
                        converter = RuntimeTypeConverter.class,
                        description = "Runtime (${COMPLETION-CANDIDATES})")
    public RuntimeType runtime = RuntimeType.main;

    @CommandLine.Option(names = { "--from-version" },
                        description = "Filter by Camel version (inclusive). Will start from 4.0 if no version ranges provided.")
    public String fromVersion;

    @CommandLine.Option(names = { "--to-version" },
                        description = "Filter by Camel version (exclusive)")
    public String toVersion;

    @CommandLine.Option(names = { "--from-date" },
                        description = "Filter by release date (inclusive)")
    public String fromDate;

    @CommandLine.Option(names = { "--to-date" },
                        description = "Filter by release date (exclusive)")
    public String toDate;

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by (version, date, or days)", defaultValue = "version")
    public String sort;

    @CommandLine.Option(names = { "--repo", "--repos" },
                        description = "Additional maven repositories (Use commas to separate multiple repositories)")
    public String repositories;

    @CommandLine.Option(names = { "--lts" }, description = "Only show LTS supported releases", defaultValue = "false")
    public boolean lts;

    @CommandLine.Option(names = { "--eol" }, description = "Include releases that are end-of-life", defaultValue = "true")
    public boolean eol = true;

    @CommandLine.Option(names = { "--patch" }, description = "Whether to include patch releases (x.y.z)", defaultValue = "true")
    public boolean patch = true;

    @CommandLine.Option(names = { "--rc" }, description = "Include also milestone or RC releases", defaultValue = "false")
    public boolean rc;

    @CommandLine.Option(names = { "--days" }, description = "Whether to include days since release", defaultValue = "true")
    public boolean days;

    @CommandLine.Option(names = { "--date-format" }, description = "The format to show the date (such as dd-MM-yyyy)",
                        defaultValue = DEFAULT_DATE_FORMAT)
    public String dateFormat;

    @CommandLine.Option(names = { "--tail" },
                        description = "The number of lines from the end of the table to show.")
    public int tail;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources",
                        defaultValue = "false")
    public boolean fresh;

    @CommandLine.Option(names = { "--download" }, defaultValue = "true",
                        description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    public boolean download = true;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format", defaultValue = "false")
    public boolean jsonOutput;

    public VersionList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
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
        // only show latest by default
        if (fromVersion == null && toVersion == null) {
            fromVersion = "4.0";
        }

        // only download if fresh, using a custom repo, or special runtime based
        List<String[]> versions = new ArrayList<>();
        if (download || fresh || repositories != null || runtime != RuntimeType.main) {
            downloadReleases(versions);
        }

        CamelCatalog catalog = new DefaultCamelCatalog();
        List<ReleaseModel> releases = RuntimeType.quarkus == runtime ? catalog.camelQuarkusReleases() : catalog.camelReleases();

        List<Row> rows = new ArrayList<>();
        filterVersions(versions, rows, releases);

        if (lts) {
            rows.removeIf(r -> !"lts".equalsIgnoreCase(r.kind));
        }
        if (!rc) {
            rows.removeIf(r -> "rc".equalsIgnoreCase(r.kind));
        }
        if (!patch) {
            rows.removeIf(r -> {
                String last = StringHelper.afterLast(r.coreVersion, ".");
                return !"0".equals(last);
            });
        }
        if (!eol) {
            SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD);
            Date now = new Date();
            rows.removeIf(r -> {
                String eol = r.eolDate;
                if (eol != null) {
                    try {
                        Date d = sdf.parse(r.eolDate);
                        return d.before(now);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                return false;
            });
        }

        // sort rows
        rows.sort(this::sortRow);

        if (tail > 0) {
            int pos = rows.size() - tail;
            if (pos > 0) {
                rows = rows.subList(pos, rows.size());
            }
        }

        JsonObject checker = loadCheckerFile();
        if (fresh && download) {
            checker = updateCheckerFile(checker, runtime.runtime(), repositories);
        }

        if (jsonOutput) {
            printer().println(
                    Jsoner.serialize(
                            rows.stream()
                                    .map(row -> new VersionListDTO(
                                            row.coreVersion, runtime.runtime(), row.runtimeVersion, row.jdks, row.kind,
                                            row.releaseDate,
                                            row.eolDate))
                                    .map(VersionListDTO::toMap)
                                    .collect(Collectors.toList())));
        } else {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("CAMEL VERSION")
                            .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.coreVersion),
                    new Column().header("QUARKUS").visible(RuntimeType.quarkus == runtime)
                            .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.runtimeVersion),
                    new Column().header("SPRING-BOOT").visible(RuntimeType.springBoot == runtime)
                            .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER).with(r -> r.runtimeVersion),
                    new Column().header("JDK")
                            .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT).with(this::jdkVersion),
                    new Column().header("KIND")
                            .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT).with(this::kind),
                    new Column().header("RELEASED")
                            .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT).with(this::releaseDate),
                    new Column().header("SUPPORTED UNTIL")
                            .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT).with(this::eolDate),
                    new Column().header("DAYS").visible(days)
                            .headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT).with(this::daysAgo))));
            String date = null;
            if (checker != null) {
                JsonArray arr = checker.getCollection("checker");
                if (repositories == null) {
                    repositories = "";
                }
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject e = arr.getMap(i);
                    boolean match = e.getString("runtime").equals(runtime.runtime())
                            && e.getStringOrDefault("repo", "").equals(repositories);
                    if (match) {
                        date = e.getString("lastUpdated");
                        break;
                    }
                }
            }
            if (date != null) {
                printer().println("Last updated: " + date + " (use --fresh to update list from internet)");
            } else {
                printer().println("Last updated: none (use --fresh to update list from internet)");
            }
        }

        return 0;
    }

    public static JsonObject updateCheckerFile(JsonObject root, String runtime, String repositories) {
        if (repositories == null) {
            repositories = "";
        }
        if (root == null) {
            root = new JsonObject();
        }
        JsonArray arr = root.getCollection("checker");
        if (arr == null) {
            arr = new JsonArray();
            root.put("checker", arr);
        }

        JsonObject target = null;
        for (int i = 0; i < arr.size(); i++) {
            JsonObject e = arr.getMap(i);
            boolean match = e.getString("runtime").equals(runtime)
                    && e.getStringOrDefault("repo", "").equals(repositories);
            if (match) {
                target = e;
                break;
            }
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        if (target == null) {
            target = new JsonObject();
            target.put("runtime", runtime);
            if (!repositories.isBlank()) {
                target.put("repo", repositories);
            }
            arr.add(target);
        }
        target.put("lastUpdated", today);

        String json = Jsoner.serialize(root);
        try {
            Path f = CommandLineHelper.getHomeDir().resolve(VERSION_LIST_CHECKER);
            Files.writeString(f, json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            // ignore
        }
        return root;
    }

    static JsonObject loadCheckerFile() {
        try {
            Path f = CommandLineHelper.getHomeDir().resolve(VERSION_LIST_CHECKER);
            if (Files.exists(f)) {
                String json = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(json);
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    protected Integer downloadReleases(List<String[]> answer) {
        KameletMain main = new KameletMain(CAMEL_INSTANCE_TYPE);

        try {
            main.setFresh(fresh);
            main.setDownload(download);
            main.setRepositories(repositories);
            main.start();

            // use kamelet-main to download from maven
            MavenDependencyDownloader downloader = main.getCamelContext().hasService(MavenDependencyDownloader.class);

            String g = "org.apache.camel";
            String a = "camel-core";
            if (RuntimeType.springBoot == runtime) {
                g = "org.apache.camel.springboot";
                a = "camel-spring-boot";
            } else if (RuntimeType.quarkus == runtime) {
                g = "org.apache.camel.quarkus";
                a = "camel-quarkus-catalog";
            }

            RepositoryResolver rr = downloader.getRepositoryResolver();
            if (rr != null) {
                repositories = rr.resolveRepository(repositories);
            }

            var versions = downloader.resolveAvailableVersions(g, a, fromVersion, repositories);
            versions = versions.stream().filter(v -> acceptVersion(v[0])).toList();
            answer.addAll(versions);

            main.stop();
        } catch (Exception e) {
            printer().println("Error downloading available Camel versions due to: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    private void filterVersions(List<String[]> versions, List<Row> rows, List<ReleaseModel> releases) throws Exception {
        for (String[] v : versions) {
            Row row = new Row();
            row.coreVersion = v[0];
            row.runtimeVersion = v[1];

            // enrich with details from catalog (if we can find any)
            String catalogVersion = RuntimeType.quarkus == runtime ? v[1] : v[0];
            ReleaseModel rm = releases.stream().filter(r -> catalogVersion.equals(r.getVersion())).findFirst().orElse(null);
            if (download && rm == null) {
                // unknown release but if it's an Apache Camel release we can grab from online
                int dots = StringHelper.countChar(v[0], '.');
                if (dots == 2) {
                    rm = onlineRelease(runtime, row.coreVersion);
                }
            }
            if (rm != null) {
                row.releaseDate = rm.getDate();
                row.daysSince = daysSince(rm.getDate());
                row.eolDate = rm.getEol();
                row.jdks = rm.getJdk();
                row.kind = rm.getKind();
            }
            boolean accept = true;
            if (fromVersion != null || toVersion != null) {
                if (fromVersion == null) {
                    fromVersion = "1.0";
                }
                if (toVersion == null) {
                    toVersion = "99.0";
                }
                accept = VersionHelper.isBetween(row.coreVersion, fromVersion, toVersion);
            }
            if (accept && fromDate != null || toDate != null) {
                if (fromDate == null) {
                    fromDate = "2000-01-01";
                }
                if (toDate == null) {
                    toDate = "9999-01-01";
                }
                accept = row.releaseDate == null || isDateBetween(row.releaseDate, fromDate, toDate);
            }
            if (accept) {
                rows.add(row);
            }
        }
        for (ReleaseModel rm : releases) {
            boolean accept = true;
            if (fromVersion != null || toVersion != null) {
                if (fromVersion == null) {
                    fromVersion = "1.0";
                }
                if (toVersion == null) {
                    toVersion = "99.0";
                }
                accept = VersionHelper.isBetween(rm.getVersion(), fromVersion, toVersion);
            }
            if (accept && fromDate != null || toDate != null) {
                if (fromDate == null) {
                    fromDate = "2000-01-01";
                }
                if (toDate == null) {
                    toDate = "9999-01-01";
                }
                accept = rm.getDate() == null || isDateBetween(rm.getDate(), fromDate, toDate);
            }
            if (accept) {
                // only add if this is a new fresh release
                if (rows.stream().filter(r -> r.coreVersion.equals(rm.getVersion())).findAny().isEmpty()) {
                    Row row = new Row();
                    rows.add(row);
                    row.coreVersion = rm.getVersion();
                    row.releaseDate = rm.getDate();
                    row.daysSince = daysSince(rm.getDate());
                    row.eolDate = rm.getEol();
                    row.jdks = rm.getJdk();
                    row.kind = rm.getKind();
                }
            }
        }
    }

    private long daysSince(String date) {
        if (date != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD);
                Date d = sdf.parse(date);
                Date d2 = new Date();
                return ChronoUnit.DAYS.between(d.toInstant(), d2.toInstant());
            } catch (Exception e) {
                // ignore
            }
        }
        return -1;
    }

    private boolean isDateBetween(String date, String from, String to) {
        try {
            var df = DateTimeFormatter.ofPattern(YYYY_MM_DD);
            LocalDate d = LocalDate.parse(date, df);
            LocalDate d2 = LocalDate.parse(from, df);
            LocalDate d3 = LocalDate.parse(to, df);
            return (d.isEqual(d2) || d.isAfter(d2)) && d.isBefore(d3);
        } catch (Exception e) {
            // ignore
        }
        return true;
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
            case "days":
                return Long.compare(o2.daysSince, o1.daysSince) * negate;
            default:
                return 0;
        }
    }

    private String jdkVersion(Row r) {
        return r.jdks;
    }

    private String kind(Row r) {
        if (r.kind != null && !"legacy".equalsIgnoreCase(r.kind)) {
            return r.kind.toUpperCase(Locale.ROOT);
        }
        return "";
    }

    private String daysAgo(Row r) {
        if (r.daysSince > -1) {
            return "" + r.daysSince;
        }
        return "";
    }

    private String releaseDate(Row r) {
        try {
            if (r.releaseDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD);
                Date d = sdf.parse(r.releaseDate);
                SimpleDateFormat sdf2 = new SimpleDateFormat(dateFormat, Locale.US);
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
                SimpleDateFormat sdf2 = new SimpleDateFormat(dateFormat, Locale.US);
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

    private ReleaseModel onlineRelease(RuntimeType runtime, String coreVersion) throws Exception {
        String gitUrl = String.format(RuntimeType.quarkus == runtime ? GIT_CAMEL_QUARKUS_URL : GIT_CAMEL_URL, coreVersion);

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
        long daysSince = -1;
        String eolDate;
        String kind;
        String jdks;
    }

}
