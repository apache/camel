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
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "doc",
                     description = "Shows documentation for kamelet, component, and other Camel resources")
public class CatalogDoc extends CamelCommand {

    @CommandLine.Parameters(description = "Name of kamelet, component, dataformat, or other Camel resource",
                            arity = "1")
    String name;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter option listed in tables by name, description, or group")
    String filter;

    @CommandLine.Option(names = { "--header" },
                        description = "Whether to display component message headers", defaultValue = "true")
    boolean headers;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version", defaultValue = "0.9.0")
    String kameletsVersion;

    // TODO: kamelet
    // TODO: endpoint uri to document the uri only

    final CamelCatalog catalog = new DefaultCamelCatalog(true);

    public CatalogDoc(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        String prefix = StringHelper.before(name, ":");
        if (prefix != null) {
            name = StringHelper.after(name, ":");
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
            System.out.println("Camel resource: " + name + " not found");
        } else {
            System.out.println("Camel " + prefix + ": " + name + " not found");
        }
        return 1;
    }

    private void docKamelet(KameletModel model) {

    }

    private void docComponent(ComponentModel cm) {
        if (cm.isDeprecated()) {
            System.out.printf("Component Name: %s (deprecated)%n", cm.getName());
        } else {
            System.out.printf("Component Name: %s%n", cm.getName());
        }
        System.out.printf("Since: %s%n", cm.getFirstVersionShort());
        System.out.println("");
        if (cm.isProducerOnly()) {
            System.out.println("Only producer is supported");
        } else if (cm.isConsumerOnly()) {
            System.out.println("Only consumer is supported");
        } else {
            System.out.println("Both producer and consumer are supported");
        }

        System.out.println("");
        System.out.printf("%s%n", cm.getDescription());
        System.out.println("");
        System.out.println("    <dependency>");
        System.out.println("        <groupId>" + cm.getGroupId() + "</groupId>");
        System.out.println("        <artifactId>" + cm.getArtifactId() + "</artifactId>");
        System.out.println("        <version>" + cm.getVersion() + "</version>");
        System.out.println("    </dependency>");
        System.out.println("");
        System.out.printf("The %s endpoint is configured using URI syntax:%n", cm.getName());
        System.out.println("");
        System.out.printf("    %s%n", cm.getSyntax());
        System.out.println("");
        System.out.println("with the following path and query parameters:");
        System.out.println("");
        System.out.printf("Path parameters (%s):%n", cm.getEndpointPathOptions().size());
        System.out.println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, cm.getEndpointPathOptions(), Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(40)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(BaseOptionModel::getShortJavaType))));
        System.out.println("");
        var filtered = filter(filter, cm.getEndpointParameterOptions());
        var total1 = cm.getEndpointParameterOptions().size();
        var total2 = filtered.size();
        if (total1 == total2) {
            System.out.printf("Query parameters (total: %s):%n", total1);
        } else {
            System.out.printf("Query parameters (total: %s match-filter: %s):%n", total1, total2);
        }
        System.out.println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, filtered, Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(40)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(BaseOptionModel::getShortJavaType))));
        System.out.println("");

        if (headers && !cm.getEndpointHeaders().isEmpty()) {
            System.out.printf("The %s component supports (total: %s) message headers, which are listed below.%n%n",
                    cm.getName(), cm.getEndpointHeaders().size());
            System.out.println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, cm.getEndpointHeaders(), Arrays.asList(
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20)
                            .maxWidth(30, OverflowBehaviour.NEWLINE)
                            .with(this::getName),
                    new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::getDescription),
                    new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.NEWLINE)
                            .with(r -> r.getShortDefaultValue(40)),
                    new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(BaseOptionModel::getShortJavaType))));
            System.out.println("");
        }
    }

    private void docDataFormat(DataFormatModel dm) {
        if (dm.isDeprecated()) {
            System.out.printf("Dataformat Name: %s (deprecated)%n", dm.getName());
        } else {
            System.out.printf("Dataformat Name: %s%n", dm.getName());
        }
        System.out.printf("Since: %s%n", dm.getFirstVersionShort());
        System.out.println("");
        System.out.printf("%s%n", dm.getDescription());
        System.out.println("");
        System.out.println("    <dependency>");
        System.out.println("        <groupId>" + dm.getGroupId() + "</groupId>");
        System.out.println("        <artifactId>" + dm.getArtifactId() + "</artifactId>");
        System.out.println("        <version>" + dm.getVersion() + "</version>");
        System.out.println("    </dependency>");
        System.out.println("");
        var filtered = filter(filter, dm.getOptions());
        var total1 = dm.getOptions().size();
        var total2 = filtered.size();
        if (total1 == total2) {
            System.out.printf("The %s dataformat supports (total: %s) options, which are listed below.%n%n", dm.getName(),
                    total1);
        } else {
            System.out.printf("The %s dataformat supports (total: %s match-filter: %s) options, which are listed below.%n%n",
                    dm.getName(), total1, total2);
        }
        System.out.println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, filtered, Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(40)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(BaseOptionModel::getShortJavaType))));
        System.out.println("");
    }

    private void docLanguage(LanguageModel lm) {
        if (lm.isDeprecated()) {
            System.out.printf("Language Name: %s (deprecated)%n", lm.getName());
        } else {
            System.out.printf("Language Name: %s%n", lm.getName());
        }
        System.out.printf("Since: %s%n", lm.getFirstVersionShort());
        System.out.println("");
        System.out.printf("%s%n", lm.getDescription());
        System.out.println("");
        System.out.println("    <dependency>");
        System.out.println("        <groupId>" + lm.getGroupId() + "</groupId>");
        System.out.println("        <artifactId>" + lm.getArtifactId() + "</artifactId>");
        System.out.println("        <version>" + lm.getVersion() + "</version>");
        System.out.println("    </dependency>");
        System.out.println("");
        var filtered = filter(filter, lm.getOptions());
        var total1 = lm.getOptions().size();
        var total2 = filtered.size();
        if (total1 == total2) {
            System.out.printf("The %s language supports (total: %s) options, which are listed below.%n%n", lm.getName(),
                    total1);
        } else {
            System.out.printf("The %s language supports (total: %s match-filter: %s) options, which are listed below.%n%n",
                    lm.getName(), total1, total2);
        }
        System.out.println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, filtered, Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(40)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(BaseOptionModel::getShortJavaType))));
        System.out.println("");
    }

    private void docOther(OtherModel om) {
        if (om.isDeprecated()) {
            System.out.printf("Miscellaneous Name: %s (deprecated)%n", om.getName());
        } else {
            System.out.printf("Miscellaneous Name: %s%n", om.getName());
        }
        System.out.printf("Since: %s%n", om.getFirstVersionShort());
        System.out.println("");
        System.out.printf("%s%n", om.getDescription());
        System.out.println("");
        System.out.println("    <dependency>");
        System.out.println("        <groupId>" + om.getGroupId() + "</groupId>");
        System.out.println("        <artifactId>" + om.getArtifactId() + "</artifactId>");
        System.out.println("        <version>" + om.getVersion() + "</version>");
        System.out.println("    </dependency>");
        System.out.println("");
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

    List<? extends BaseOptionModel> filter(String name, List<? extends BaseOptionModel> options) {
        if (name == null || name.isEmpty()) {
            return options;
        }
        String target = name.toLowerCase(Locale.ROOT);
        return options.stream().filter(
                r -> r.getName().equalsIgnoreCase(target) || r.getDescription().toLowerCase(Locale.ROOT).contains(target)
                        || r.getShortGroup() != null && r.getShortGroup().toLowerCase(Locale.ROOT).contains(target))
                .collect(Collectors.toList());
    }

}
