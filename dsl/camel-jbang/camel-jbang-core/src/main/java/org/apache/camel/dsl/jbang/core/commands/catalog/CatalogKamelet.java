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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.dsl.jbang.core.common.CamelTableColumns;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

@CommandLine.Command(name = "kamelet",
                     description = "List Kamelets from the Kamelet Catalog", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel catalog kamelet",
                             "  camel catalog kamelet --type=source",
                             "  camel catalog kamelet --filter=kafka" })
public class CatalogKamelet extends CamelCommand {

    private static final List<String> TYPES = List.of("source", "sink", "action");

    private static final RowSort<KameletModel> ORDER = new RowSort<KameletModel>()
            .text("name", r -> r.name)
            .text("type", r -> r.type)
            .text("level", r -> r.supportLevel)
            .alias("support-level", "level")
            .text("description", r -> r.description);

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by name, type, support-level, or description", defaultValue = "name")
    String sort;

    @CommandLine.Option(names = { "--type", "--filter-type" },
                        description = "Filter by type: source, sink, or action")
    String filterType;

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter by name or description")
    String filterName;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version (auto-detected from classpath if not set)")
    String kameletsVersion;

    @CommandLine.Mixin
    MavenResolverMixin mavenResolver = new MavenResolverMixin();

    public CatalogKamelet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // the options are wrong whatever the catalog has, so before loading it
        Comparator<KameletModel> order = order();
        if (filterType != null && !TYPES.contains(filterType.toLowerCase(Locale.ROOT))) {
            throw CliException.usage("--type must be one of " + String.join(", ", TYPES) + ", was: " + filterType);
        }

        if (kameletsVersion == null) {
            kameletsVersion = VersionHelper.extractKameletsVersion();
        }

        List<KameletModel> rows;
        try {
            rows = new ArrayList<>(loadKamelets());
        } catch (Exception e) {
            printer().printErr("Cannot download camel-kamelets-catalog due to " + e.getMessage());
            return 1;
        }

        if (filterType != null) {
            rows.removeIf(r -> !filterType.equalsIgnoreCase(r.type));
        }
        if (filterName != null) {
            String text = filterName.toLowerCase(Locale.ROOT);
            // a Kamelet may have no description
            rows.removeIf(r -> !text.equalsIgnoreCase(r.name)
                    && (r.description == null || !r.description.toLowerCase(Locale.ROOT).contains(text)));
        }

        // sort rows
        rows.sort(order);

        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(CatalogKamelet::toJson).collect(Collectors.toList())));
        } else if (!rows.isEmpty()) {
            // Size the DESCRIPTION column to fill the terminal from the measured width of the other columns.
            int nameW = CamelTableColumns.measure("NAME", CamelTableColumns.NAME_MAX, rows, r -> r.name);
            int typeW = Math.max(10, CamelTableColumns.measure("TYPE", Integer.MAX_VALUE, rows, r -> r.type));
            int levelW = Math.max(12, CamelTableColumns.measure("LEVEL", Integer.MAX_VALUE, rows, r -> r.supportLevel));
            int descWidth = CamelTableColumns.lastColumnWidth(
                    terminalWidth(), TerminalWidthHelper.noBorderOverhead(4), nameW, typeW, levelW);
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    CamelTableColumns.name().with(r -> r.name),
                    new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).minWidth(10).with(r -> r.type),
                    new Column().header("LEVEL").dataAlign(HorizontalAlign.LEFT).minWidth(12).with(r -> r.supportLevel),
                    CamelTableColumns.lastText("DESCRIPTION", descWidth).with(this::getDescription))));
        }

        if (rows.isEmpty() && filterName != null) {
            // not part of the output, which can be JSON
            printer().printDiagnostic("No results for filter: " + filterName);
        }

        return 0;
    }

    /**
     * The Kamelets of the catalog of the Kamelets version.
     */
    protected List<KameletModel> loadKamelets() throws Exception {
        Map<String, Object> kamelets = KameletCatalogHelper.loadKamelets(kameletsVersion, mavenResolver.repos());
        List<KameletModel> models = new ArrayList<>();
        for (Object o : kamelets.values()) {
            models.add(KameletCatalogHelper.createModel(o, false));
        }
        return models;
    }

    private static JsonObject toJson(KameletModel r) {
        JsonObject jo = new JsonObject();
        jo.put("name", r.name);
        jo.put("type", r.type);
        jo.put("level", r.supportLevel);
        jo.put("description", r.description);
        return jo;
    }

    /**
     * The order of the Kamelets by the --sort option.
     */
    Comparator<KameletModel> order() {
        return ORDER.comparator(sort);
    }

    private String getDescription(KameletModel r) {
        String d = r.description;
        if (d != null && d.contains(".")) {
            // grab first sentence
            d = StringHelper.before(d, ".");
            d = d.trim();
        }
        if (d != null && d.contains("\n")) {
            // grab first sentence
            d = StringHelper.before(d, "\n");
            d = d.trim();
        }
        if (d == null) {
            d = "";
        }
        return d;
    }

}
