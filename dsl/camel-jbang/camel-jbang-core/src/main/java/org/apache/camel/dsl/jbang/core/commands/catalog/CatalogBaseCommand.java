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
package org.apache.camel.dsl.jbang.core.commands.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

public abstract class CatalogBaseCommand extends CamelCommand {

    @CommandLine.Option(names = { "--camel-version" },
                        description = "To use a different Camel version than the default version")
    String camelVersion;

    @CommandLine.Option(names = { "--runtime" }, completionCandidates = RuntimeCompletionCandidates.class,
                        description = "Runtime (spring-boot, quarkus, or camel-main)")
    String runtime;

    @CommandLine.Option(names = { "--quarkus-version" }, description = "Quarkus Platform version",
                        defaultValue = "3.2.5.Final")
    String quarkusVersion;

    @CommandLine.Option(names = { "--repos" },
                        description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories)")
    String repos;

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by name, support-level, or description", defaultValue = "name")
    String sort;

    @CommandLine.Option(names = { "--gav" },
                        description = "Display Maven GAV instead of name", defaultValue = "false")
    boolean gav;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter by name or description")
    String filterName;

    @CommandLine.Option(names = { "--since-before" },
                        description = "Filter by version older (inclusive)")
    String sinceBefore;

    @CommandLine.Option(names = { "--since-after" },
                        description = "Filter by version more recent (inclusive)")
    String sinceAfter;

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    CamelCatalog catalog;

    public CatalogBaseCommand(CamelJBangMain main) {
        super(main);
    }

    abstract List<Row> collectRows();

    String getGAV(ArtifactModel<?> model) {
        return model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();
    }

    CamelCatalog loadCatalog() throws Exception {
        if ("spring-boot".equals(runtime)) {
            return CatalogLoader.loadSpringBootCatalog(repos, camelVersion);
        } else if ("quarkus".equals(runtime)) {
            return CatalogLoader.loadQuarkusCatalog(repos, quarkusVersion);
        }
        if (camelVersion == null) {
            return new DefaultCamelCatalog(true);
        } else {
            return CatalogLoader.loadCatalog(repos, camelVersion);
        }
    }

    @Override
    public Integer doCall() throws Exception {
        this.catalog = loadCatalog();
        List<Row> rows = collectRows();

        if (filterName != null) {
            filterName = filterName.toLowerCase(Locale.ROOT);
            rows = rows.stream()
                    .filter(
                            r -> r.name.equalsIgnoreCase(filterName)
                                    || r.description.toLowerCase(Locale.ROOT).contains(filterName)
                                    || r.label.toLowerCase(Locale.ROOT).contains(filterName))
                    .collect(Collectors.toList());
        }
        if (sinceBefore != null) {
            rows = rows.stream()
                    .filter(r -> VersionHelper.isGE(sinceBefore, r.since))
                    .collect(Collectors.toList());
        }
        if (sinceAfter != null) {
            rows = rows.stream()
                    .filter(r -> VersionHelper.isGE(r.since, sinceAfter))
                    .collect(Collectors.toList());
        }

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            if (jsonOutput) {
                System.out.println(
                        Jsoner.serialize(
                                rows.stream().map(row -> Map.of(
                                        "name", row.name,
                                        "level", row.level,
                                        "native", row.nativeSupported)).collect(Collectors.toList())));
            } else {
                System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                        new Column().header("NAME").visible(!gav).dataAlign(HorizontalAlign.LEFT).maxWidth(30)
                                .with(r -> r.name),
                        new Column().header("ARTIFACT-ID").visible(gav).dataAlign(HorizontalAlign.LEFT).with(this::shortGav),
                        new Column().header("LEVEL").dataAlign(HorizontalAlign.LEFT).with(r -> r.level),
                        new Column().header("NATIVE").dataAlign(HorizontalAlign.CENTER)
                                .visible("quarkus".equals(runtime)).with(this::nativeSupported),
                        new Column().header("SINCE").dataAlign(HorizontalAlign.RIGHT).with(r -> r.since),
                        new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::shortDescription))));
            }
        }

        return 0;
    }

    int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            case "level":
            case "support-level":
                return o1.level.compareToIgnoreCase(o2.level) * negate;
            case "description":
                return o1.description.compareToIgnoreCase(o2.description) * negate;
            default:
                return 0;
        }
    }

    String shortGav(Row r) {
        // only output artifact id
        return MavenGav.parseGav(r.gav).getArtifactId();
    }

    String shortDescription(Row r) {
        if (r.deprecated) {
            return "DEPRECATED: " + r.description;
        } else {
            return r.description;
        }
    }

    String nativeSupported(Row r) {
        return r.nativeSupported ? "x" : "";
    }

    static String fixQuarkusSince(String since) {
        // quarkus-catalog may have 0.1 and 0.0.1 versions that are really 1.0
        if (since != null && since.startsWith("0")) {
            return "1.0";
        }
        return since;
    }

    static List<String> findComponentNames(CamelCatalog catalog) {
        List<String> answer = catalog.findComponentNames();
        List<String> copy = new ArrayList<>(answer);
        // remove empty (spring boot catalog has a bug)
        copy.removeIf(String::isBlank);
        return copy;
    }

    static class Row {
        String name;
        String title;
        String level;
        String since;
        boolean nativeSupported;
        String description;
        String label;
        String gav;
        boolean deprecated;
    }

}
