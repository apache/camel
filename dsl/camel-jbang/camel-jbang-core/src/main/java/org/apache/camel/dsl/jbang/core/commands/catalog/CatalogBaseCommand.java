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

import java.util.Arrays;
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
import org.apache.camel.main.download.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import picocli.CommandLine;

public abstract class CatalogBaseCommand extends CamelCommand {

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by name, support-level, or description", defaultValue = "name")
    String sort;

    @CommandLine.Option(names = { "--gav" },
                        description = "Display Maven GAV instead of name", defaultValue = "false")
    boolean gav;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter by name or description")
    String filterName;

    final CamelCatalog catalog = new DefaultCamelCatalog(true);

    public CatalogBaseCommand(CamelJBangMain main) {
        super(main);
    }

    abstract List<Row> collectRows();

    String getGAV(ArtifactModel<?> model) {
        return model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();
    }

    @Override
    public Integer call() throws Exception {
        List<Row> rows = collectRows();

        if (filterName != null) {
            filterName = filterName.toLowerCase(Locale.ROOT);
            rows = rows.stream().filter(
                    r -> r.name.equalsIgnoreCase(filterName) || r.description.toLowerCase(Locale.ROOT).contains(filterName)
                            || r.label.toLowerCase(Locale.ROOT).contains(filterName))
                    .collect(Collectors.toList());
        }

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("NAME").visible(!gav).dataAlign(HorizontalAlign.LEFT).with(r -> r.name),
                    new Column().header("ARTIFACT-ID").visible(gav).dataAlign(HorizontalAlign.LEFT).with(this::shortGav),
                    new Column().header("LEVEL").dataAlign(HorizontalAlign.LEFT).minWidth(12).with(r -> r.level),
                    new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::shortDescription))));
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

    static class Row {
        String name;
        String title;
        String level;
        String description;
        String label;
        String gav;
        boolean deprecated;
    }

}
