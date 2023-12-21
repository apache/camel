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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "kamelet",
                     description = "List Kamelets from the Kamelet Catalog", sortOptions = false)
public class CatalogKamelet extends CamelCommand {

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
            "--kamelets-version" }, description = "Apache Camel Kamelets version")
    String kameletsVersion;

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
            ClassLoader cl = createClassLoader();
            MavenDependencyDownloader downloader = new MavenDependencyDownloader();
            downloader.setClassLoader(cl);
            downloader.start();
            downloader.downloadDependency("org.apache.camel.kamelets", "camel-kamelets-catalog", kameletsVersion);

            Thread.currentThread().setContextClassLoader(cl);
            Class<?> clazz = cl.loadClass("org.apache.camel.kamelets.catalog.KameletsCatalog");
            Object catalog = clazz.getDeclaredConstructor().newInstance();
            Method m = clazz.getMethod("getKamelets");
            kamelets = (Map<String, Object>) ObjectHelper.invokeMethod(m, catalog);
        } catch (Exception e) {
            System.err.println("Cannot download camel-kamelets-catalog due to " + e.getMessage());
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
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).with(r -> r.name),
                    new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).minWidth(10).with(r -> r.type),
                    new Column().header("LEVEL").dataAlign(HorizontalAlign.LEFT).minWidth(12).with(r -> r.supportLevel),
                    new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::getDescription))));
        }

        return 0;
    }

    protected int sortRow(KameletModel o1, KameletModel o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            case "type":
                return o1.type.compareToIgnoreCase(o2.type) * negate;
            case "level":
            case "support-level":
                return o1.supportLevel.compareToIgnoreCase(o2.supportLevel) * negate;
            case "description":
                return o1.description.compareToIgnoreCase(o2.description) * negate;
            default:
                return 0;
        }
    }

    private ClassLoader createClassLoader() {
        ClassLoader parentCL = CatalogKamelet.class.getClassLoader();
        return new DependencyDownloaderClassLoader(parentCL);
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
