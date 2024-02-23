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

import java.awt.*;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.main.util.SuggestSimilarHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.commands.catalog.CatalogBaseCommand.findComponentNames;

@CommandLine.Command(name = "doc",
                     description = "Shows documentation for kamelet, component, and other Camel resources", sortOptions = false)
public class CatalogDoc extends CamelCommand {

    @CommandLine.Parameters(description = "Name of kamelet, component, dataformat, or other Camel resource",
                            arity = "1")
    String name;

    @CommandLine.Option(names = { "--camel-version" },
                        description = "To use a different Camel version than the default version")
    String camelVersion;

    @CommandLine.Option(names = { "--runtime" }, completionCandidates = RuntimeCompletionCandidates.class,
                        description = "Runtime (spring-boot, quarkus, or camel-main)")
    String runtime;

    @CommandLine.Option(names = { "--repos" },
                        description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories)")
    String repos;

    @CommandLine.Option(names = { "--url" },
                        description = "Prints the link to the online documentation on the Camel website",
                        defaultValue = "false")
    boolean url;

    @CommandLine.Option(names = { "--open-url" },
                        description = "Opens the online documentation form the Camel website in the web browser",
                        defaultValue = "false")
    boolean openUrl;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter option listed in tables by name, description, or group")
    String filter;

    @CommandLine.Option(names = { "--header" },
                        description = "Whether to display component message headers", defaultValue = "true")
    boolean headers;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version", defaultValue = "4.4.0")
    String kameletsVersion;

    CamelCatalog catalog;

    public CatalogDoc(CamelJBangMain main) {
        super(main);
    }

    CamelCatalog loadCatalog() throws Exception {
        if ("spring-boot".equals(runtime)) {
            return CatalogLoader.loadSpringBootCatalog(repos, camelVersion);
        } else if ("quarkus".equals(runtime)) {
            return CatalogLoader.loadQuarkusCatalog(repos, camelVersion);
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

        String prefix = StringHelper.before(name, ":");
        if (prefix != null) {
            name = StringHelper.after(name, ":");
        }

        // special for camel-main
        if ("main".equals(name)) {
            MainModel mm = catalog.mainModel();
            if (mm != null) {
                docMain(mm);
                return 0;
            }
        }

        if (prefix == null || "kamelet".equals(prefix)) {
            KameletModel km = KameletCatalogHelper.loadKameletModel(name, kameletsVersion);
            if (km != null) {
                docKamelet(km);
                return 0;
            }
        }
        if (prefix == null || "component".equals(prefix)) {
            ComponentModel cm = catalog.componentModel(name);
            if (cm != null) {
                docComponent(cm);
                return 0;
            }
        }
        if (prefix == null || "dataformat".equals(prefix)) {
            DataFormatModel dm = catalog.dataFormatModel(name);
            if (dm != null) {
                docDataFormat(dm);
                return 0;
            }
        }
        if (prefix == null || "language".equals(prefix)) {
            LanguageModel lm = catalog.languageModel(name);
            if (lm != null) {
                docLanguage(lm);
                return 0;
            }
        }
        if (prefix == null || "other".equals(prefix)) {
            OtherModel om = catalog.otherModel(name);
            if (om != null) {
                docOther(om);
                return 0;
            }
        }

        if (prefix == null) {
            // guess if a kamelet
            List<String> suggestions;
            boolean kamelet = name.endsWith("-sink") || name.endsWith("-source") || name.endsWith("-action");
            if (kamelet) {
                // kamelet names
                suggestions = SuggestSimilarHelper.didYouMean(KameletCatalogHelper.findKameletNames(kameletsVersion), name);
            } else {
                // assume its a component
                suggestions = SuggestSimilarHelper.didYouMean(findComponentNames(catalog), name);
            }
            if (suggestions != null) {
                String type = kamelet ? "kamelet" : "component";
                printer().printf("Camel %s: %s not found. Did you mean? %s%n", type, name, String.join(", ", suggestions));
            } else {
                printer().println("Camel resource: " + name + " not found");
            }
        } else {
            List<String> suggestions = null;
            if ("kamelet".equals(prefix)) {
                suggestions = SuggestSimilarHelper.didYouMean(KameletCatalogHelper.findKameletNames(kameletsVersion), name);
            } else if ("component".equals(prefix)) {
                suggestions = SuggestSimilarHelper.didYouMean(findComponentNames(catalog), name);
            } else if ("dataformat".equals(prefix)) {
                suggestions = SuggestSimilarHelper.didYouMean(catalog.findDataFormatNames(), name);
            } else if ("language".equals(prefix)) {
                suggestions = SuggestSimilarHelper.didYouMean(catalog.findLanguageNames(), name);
            } else if ("other".equals(prefix)) {
                suggestions = SuggestSimilarHelper.didYouMean(catalog.findOtherNames(), name);
            }
            if (suggestions != null) {
                printer().printf("Camel %s: %s not found. Did you mean? %s%n", prefix, name, String.join(", ", suggestions));
            } else {
                printer().printf("Camel %s: %s not found.%n", prefix, name);
            }
        }
        return 1;
    }

    private void docKamelet(KameletModel km) throws Exception {
        String link = websiteLink("kamelet", name, kameletsVersion);
        if (openUrl) {
            if (link != null) {
                Desktop.getDesktop().browse(new URI(link));
            }
            return;
        }
        if (url) {
            if (link != null) {
                printer().println(link);
            }
            return;
        }

        printer().printf("Kamelet Name: %s%n", km.name);
        printer().printf("Kamelet Type: %s%n", km.type);
        printer().println("Support Level: " + km.supportLevel);
        printer().println("");
        printer().printf("%s%n", km.description);
        printer().println("");
        if (km.dependencies != null && !km.dependencies.isEmpty()) {
            printer().println("");
            for (String dep : km.dependencies) {
                MavenGav gav = MavenGav.parseGav(dep);
                if ("camel-core".equals(gav.getArtifactId())) {
                    // camel-core is implied so skip
                    continue;
                }
                printer().println("    <dependency>");
                printer().println("        <groupId>" + gav.getGroupId() + "</groupId>");
                printer().println("        <artifactId>" + gav.getArtifactId() + "</artifactId>");
                String v = gav.getVersion();
                if (v == null && "org.apache.camel".equals(gav.getGroupId())) {
                    v = catalog.getCatalogVersion();
                }
                if (v != null) {
                    printer().println("        <version>" + v + "</version>");
                }
                printer().println("    </dependency>");
            }
            printer().println("");
        }
        if (km.properties != null && !km.properties.isEmpty()) {
            var filtered = filterKameletOptions(filter, km.properties.values());
            int total1 = km.properties.size();
            var total2 = filtered.size();
            if (total1 == total2) {
                printer().printf("The %s kamelet supports (total: %s) options, which are listed below.%n%n", km.name, total1);
            } else {
                printer().printf("The %s kamelet supports (total: %s match-filter: %s) options, which are listed below.%n%n",
                        km.name, total1, total2);
            }
            printer().println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, filtered, Arrays.asList(
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20)
                            .maxWidth(35, OverflowBehaviour.NEWLINE)
                            .with(r -> r.name),
                    new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(this::getDescription),
                    new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                            .with(r -> r.defaultValue),
                    new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                            .with(r -> r.type),
                    new Column().header("EXAMPLE").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.NEWLINE)
                            .with(r -> r.example))));
            printer().println("");
        }

        if (link != null) {
            printer().println(link);
            printer().println("");
        }
    }

    private void docMain(MainModel mm) throws Exception {
        String link = websiteLink("other", name, catalog.getCatalogVersion());
        if (openUrl) {
            if (link != null) {
                Desktop.getDesktop().browse(new URI(link));
            }
            return;
        }
        if (url) {
            if (link != null) {
                printer().println(link);
            }
            return;
        }

        printer().printf("Name: %s%n", "main");
        printer().printf("Since: %s%n", "3.0");
        printer().println("");
        printer().printf("%s%n",
                "This module is used for running Camel standalone via a main class extended from camel-main.");
        printer().println("");
        printer().println("    <dependency>");
        printer().println("        <groupId>" + "org.apache.camel" + "</groupId>");
        printer().println("        <artifactId>" + "camel.main" + "</artifactId>");
        printer().println("        <version>" + catalog.getCatalogVersion() + "</version>");
        printer().println("    </dependency>");
        printer().println("");

        printer().printf("%s%n%n%n",
                "When running Camel via camel-main you can configure Camel in the application.properties file");

        for (MainModel.MainGroupModel g : mm.getGroups()) {
            var go = filterMain(g.getName(), null, mm.getOptions());
            var filtered = filter(filter, go);
            var total1 = go.size();
            var total2 = filtered.size();
            if (total2 > 0) {
                if (total1 == total2) {
                    printer().printf("%s (total: %s):%n", g.getDescription(), total1);
                } else {
                    printer().printf("%s options (total: %s match-filter: %s):%n", g.getDescription(), total1, total2);
                }
                printer().println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, filtered, Arrays.asList(
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20)
                                .maxWidth(40, OverflowBehaviour.NEWLINE)
                                .with(this::getName),
                        new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(80, OverflowBehaviour.NEWLINE)
                                .with(this::getDescription),
                        new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                                .with(r -> r.getShortDefaultValue(25)),
                        new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                                .with(BaseOptionModel::getShortJavaType))));
                printer().println("");
                printer().println("");
            }
        }

        if (link != null) {
            printer().println(link);
            printer().println("");
        }
    }

    private void docComponent(ComponentModel cm) throws Exception {
        String link = websiteLink("component", name, catalog.getCatalogVersion());
        if (openUrl) {
            if (link != null) {
                Desktop.getDesktop().browse(new URI(link));
            }
            return;
        }
        if (url) {
            if (link != null) {
                printer().println(link);
            }
            return;
        }

        if (cm.isDeprecated()) {
            printer().printf("Component Name: %s (deprecated)%n", cm.getName());
        } else {
            printer().printf("Component Name: %s%n", cm.getName());
        }
        printer().printf("Since: %s%n", fixQuarkusSince(cm.getFirstVersionShort()));
        printer().println("");
        if (cm.isProducerOnly()) {
            printer().println("Only producer is supported");
        } else if (cm.isConsumerOnly()) {
            printer().println("Only consumer is supported");
        } else {
            printer().println("Both producer and consumer are supported");
        }

        printer().println("");
        printer().printf("%s%n", cm.getDescription());
        printer().println("");
        printer().println("    <dependency>");
        printer().println("        <groupId>" + cm.getGroupId() + "</groupId>");
        printer().println("        <artifactId>" + cm.getArtifactId() + "</artifactId>");
        printer().println("        <version>" + cm.getVersion() + "</version>");
        printer().println("    </dependency>");
        printer().println("");
        printer().printf("The %s endpoint is configured using URI syntax:%n", cm.getName());
        printer().println("");
        printer().printf("    %s%n", cm.getSyntax());
        printer().println("");
        printer().println("with the following path and query parameters:");
        printer().println("");
        printer().printf("Path parameters (%s):%n", cm.getEndpointPathOptions().size());
        printer().println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, cm.getEndpointPathOptions(), Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(35, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                        .with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(25)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                        .with(BaseOptionModel::getShortJavaType))));
        printer().println("");
        var filtered = filter(filter, cm.getEndpointParameterOptions());
        var total1 = cm.getEndpointParameterOptions().size();
        var total2 = filtered.size();
        if (total1 == total2) {
            printer().printf("Query parameters (total: %s):%n", total1);
        } else {
            printer().printf("Query parameters (total: %s match-filter: %s):%n", total1, total2);
        }
        printer().println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, filtered, Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(35, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                        .with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(25)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                        .with(BaseOptionModel::getShortJavaType))));
        printer().println("");

        if (headers && !cm.getEndpointHeaders().isEmpty()) {
            printer().printf("The %s component supports (total: %s) message headers, which are listed below.%n%n",
                    cm.getName(), cm.getEndpointHeaders().size());
            printer().println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, cm.getEndpointHeaders(), Arrays.asList(
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20)
                            .maxWidth(35, OverflowBehaviour.NEWLINE)
                            .with(this::getName),
                    new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(this::getDescription),
                    new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                            .with(r -> r.getShortDefaultValue(25)),
                    new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                            .with(BaseOptionModel::getShortJavaType))));
            printer().println("");
        }

        if (link != null) {
            printer().println(link);
            printer().println("");
        }
    }

    private void docDataFormat(DataFormatModel dm) throws Exception {
        String link = websiteLink("dataformat", name, catalog.getCatalogVersion());
        if (openUrl) {
            if (link != null) {
                Desktop.getDesktop().browse(new URI(link));
            }
            return;
        }
        if (url) {
            if (link != null) {
                printer().println(link);
            }
            return;
        }

        if (dm.isDeprecated()) {
            printer().printf("Dataformat Name: %s (deprecated)%n", dm.getName());
        } else {
            printer().printf("Dataformat Name: %s%n", dm.getName());
        }
        printer().printf("Since: %s%n", fixQuarkusSince(dm.getFirstVersionShort()));
        printer().println("");
        printer().printf("%s%n", dm.getDescription());
        printer().println("");
        printer().println("    <dependency>");
        printer().println("        <groupId>" + dm.getGroupId() + "</groupId>");
        printer().println("        <artifactId>" + dm.getArtifactId() + "</artifactId>");
        printer().println("        <version>" + dm.getVersion() + "</version>");
        printer().println("    </dependency>");
        printer().println("");
        var filtered = filter(filter, dm.getOptions());
        var total1 = dm.getOptions().size();
        var total2 = filtered.size();
        if (total1 == total2) {
            printer().printf("The %s dataformat supports (total: %s) options, which are listed below.%n%n", dm.getName(),
                    total1);
        } else {
            printer().printf("The %s dataformat supports (total: %s match-filter: %s) options, which are listed below.%n%n",
                    dm.getName(), total1, total2);
        }
        printer().println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, filtered, Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(35, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                        .with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(25)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                        .with(BaseOptionModel::getShortJavaType))));
        printer().println("");

        if (link != null) {
            printer().println(link);
            printer().println("");
        }
    }

    private void docLanguage(LanguageModel lm) throws Exception {
        String link = websiteLink("language", name, catalog.getCatalogVersion());
        if (openUrl) {
            if (link != null) {
                Desktop.getDesktop().browse(new URI(link));
            }
            return;
        }
        if (url) {
            if (link != null) {
                printer().println(link);
            }
            return;
        }

        if (lm.isDeprecated()) {
            printer().printf("Language Name: %s (deprecated)%n", lm.getName());
        } else {
            printer().printf("Language Name: %s%n", lm.getName());
        }
        printer().printf("Since: %s%n", fixQuarkusSince(lm.getFirstVersionShort()));
        printer().println("");
        printer().printf("%s%n", lm.getDescription());
        printer().println("");
        printer().println("    <dependency>");
        printer().println("        <groupId>" + lm.getGroupId() + "</groupId>");
        printer().println("        <artifactId>" + lm.getArtifactId() + "</artifactId>");
        printer().println("        <version>" + lm.getVersion() + "</version>");
        printer().println("    </dependency>");
        printer().println("");
        var filtered = filter(filter, lm.getOptions());
        var total1 = lm.getOptions().size();
        var total2 = filtered.size();
        if (total1 == total2) {
            printer().printf("The %s language supports (total: %s) options, which are listed below.%n%n", lm.getName(),
                    total1);
        } else {
            printer().printf("The %s language supports (total: %s match-filter: %s) options, which are listed below.%n%n",
                    lm.getName(), total1, total2);
        }
        printer().println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, filtered, Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(35, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                        .with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(25)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.NEWLINE)
                        .with(BaseOptionModel::getShortJavaType))));
        printer().println("");

        if (link != null) {
            printer().println(link);
            printer().println("");
        }
    }

    private void docOther(OtherModel om) throws Exception {
        String link = websiteLink("other", name, catalog.getCatalogVersion());
        if (openUrl) {
            if (link != null) {
                Desktop.getDesktop().browse(new URI(link));
            }
            return;
        }
        if (url) {
            if (link != null) {
                printer().println(link);
            }
            return;
        }

        if (om.isDeprecated()) {
            printer().printf("Miscellaneous Name: %s (deprecated)%n", om.getName());
        } else {
            printer().printf("Miscellaneous Name: %s%n", om.getName());
        }
        printer().printf("Since: %s%n", fixQuarkusSince(om.getFirstVersionShort()));
        printer().println("");
        printer().printf("%s%n", om.getDescription());
        printer().println("");
        printer().println("    <dependency>");
        printer().println("        <groupId>" + om.getGroupId() + "</groupId>");
        printer().println("        <artifactId>" + om.getArtifactId() + "</artifactId>");
        printer().println("        <version>" + om.getVersion() + "</version>");
        printer().println("    </dependency>");
        printer().println("");

        if (link != null) {
            printer().println(link);
            printer().println("");
        }
    }

    String getName(BaseOptionModel o) {
        String l = o.getShortGroup();
        if (l != null && !"common".equals(l)) {
            return o.getName() + "\n" + "(" + l + ")";
        }
        return o.getName();
    }

    String getDescription(BaseOptionModel o) {
        String prefix = "";
        String suffix = "";
        if (o.isDeprecated()) {
            prefix = "DEPRECATED: " + prefix;
        }
        if (o.isRequired()) {
            prefix = "REQUIRED: " + prefix;
        }
        if (o.getEnums() != null) {
            suffix = "\n\nEnum values:\n- " + String.join("\n- ", o.getEnums());
        }
        return prefix + o.getDescription() + suffix;
    }

    String getDescription(KameletOptionModel o) {
        String prefix = "";
        String suffix = "";
        if (o.required) {
            prefix = "REQUIRED: " + prefix;
        }
        if (o.enumValues != null) {
            suffix = "\n\nEnum values:\n- " + String.join("\n- ", o.enumValues);
        }
        return prefix + o.description + suffix;
    }

    List<? extends BaseOptionModel> filter(String name, List<? extends BaseOptionModel> options) {
        if (name == null || name.isEmpty()) {
            return options;
        }
        String target = name.toLowerCase(Locale.ROOT);
        return options.stream().filter(
                r -> r.getName().contains(target) || r.getName().equalsIgnoreCase(target)
                        || r.getDescription().toLowerCase(Locale.ROOT).contains(target)
                        || r.getShortGroup() != null && r.getShortGroup().toLowerCase(Locale.ROOT).contains(target))
                .collect(Collectors.toList());
    }

    Collection<KameletOptionModel> filterKameletOptions(String name, Collection<KameletOptionModel> options) {
        if (name == null || name.isEmpty()) {
            return options;
        }
        String target = name.toLowerCase(Locale.ROOT);
        return options.stream().filter(
                r -> r.name.equalsIgnoreCase(target) || r.description.toLowerCase(Locale.ROOT).contains(target))
                .collect(Collectors.toList());
    }

    String websiteLink(String prefix, String name, String version) {
        String v = "next";
        if (version != null && !version.endsWith("-SNAPSHOT")) {
            // 3.18.2 -> 3.18.x
            int pos = version.lastIndexOf('.');
            v = version.substring(0, pos) + ".x";
        }
        if ("component".equals(prefix)) {
            return String.format("https://camel.apache.org/components/%s/%s-component.html", v, name);
        } else if ("dataformat".equals(prefix)) {
            return String.format("https://camel.apache.org/components/%s/dataformats/%s-dataformat.html", v, name);
        } else if ("language".equals(prefix)) {
            return String.format("https://camel.apache.org/components/%s/languages/%s-language.html", v, name);
        } else if ("other".equals(prefix)) {
            return String.format("https://camel.apache.org/components/%s/others/%s.html", v, name);
        } else if ("kamelet".equals(prefix)) {
            return String.format("https://camel.apache.org/camel-kamelets/%s/%s.html", v, name);
        }

        return null;
    }

    List<? extends BaseOptionModel> filterMain(String prefix, String name, List<? extends BaseOptionModel> options) {
        options = options.stream().filter(o -> o.getName().startsWith(prefix)).collect(Collectors.toList());

        if (name == null || name.isEmpty()) {
            return options;
        }
        String target = name.toLowerCase(Locale.ROOT);
        return options.stream().filter(
                r -> r.getName().contains(target) || r.getName().equalsIgnoreCase(target)
                        || r.getDescription().toLowerCase(Locale.ROOT).contains(target)
                        || r.getShortGroup() != null && r.getShortGroup().toLowerCase(Locale.ROOT).contains(target))
                .collect(Collectors.toList());
    }

    static String fixQuarkusSince(String since) {
        // quarkus-catalog may have 0.1 and 0.0.1 versions that are really 1.0
        if (since != null && since.startsWith("0")) {
            return "1.0";
        }
        return since;
    }

}
