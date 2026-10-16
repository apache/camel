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
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "kamelet",
                     description = "List Kamelets from the Kamelet Catalog", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel catalog kamelet",
                             "  camel catalog kamelet --type=source",
                             "  camel catalog kamelet --filter=kafka" })
public class CatalogKamelet extends CamelCommand {

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
        List<KameletModel> rows = new ArrayList<>();

        if (kameletsVersion == null) {
            kameletsVersion = VersionHelper.extractKameletsVersion();
        }

        Map<String, Object> kamelets;
        try {
            kamelets = KameletCatalogHelper.loadKamelets(kameletsVersion, mavenResolver.repos());
        } catch (Exception e) {
            printer().printErr("Cannot download camel-kamelets-catalog due to " + e.getMessage());
            return 1;
        }

        for (Object o : kamelets.values()) {
            KameletModel row = KameletCatalogHelper.createModel(o, false);
            rows.add(row);
        }

        if (filterType != null) {
            rows = rows.stream().filter(r -> r.type.equalsIgnoreCase(filterType)).collect(Collectors.toList());
        }
        if (filterName != null) {
            filterName = filterName.toLowerCase(Locale.ROOT);
            rows = rows.stream().filter(
                    r -> r.name.equalsIgnoreCase(filterName) || r.description.toLowerCase(Locale.ROOT).contains(filterName))
                    .collect(Collectors.toList());
        }

        // sort rows
        rows.sort(order());

        if (!rows.isEmpty()) {
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

        return 0;
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
