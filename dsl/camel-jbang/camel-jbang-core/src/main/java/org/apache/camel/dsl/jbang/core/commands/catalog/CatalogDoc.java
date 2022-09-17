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
import picocli.CommandLine;

@CommandLine.Command(name = "doc",
                     description = "Shows documentation for kamelet, component, and other Camel resources")
public class CatalogDoc extends CamelCommand {

    @CommandLine.Parameters(description = "Name of kamelet, component, dataformat, or other Camel resource",
                            arity = "1")
    String name;

    final CamelCatalog catalog = new DefaultCamelCatalog(true);

    public CatalogDoc(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        ComponentModel cm = catalog.componentModel(name);
        if (cm != null) {
            docComponent(cm);
            return 0;
        }

        System.out.println("Camel resource: " + name + " not found");
        return 1;
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
        System.out.printf("Query parameters (%s):%n", cm.getEndpointParameterOptions().size());
        System.out.println(AsciiTable.getTable(AsciiTable.FANCY_ASCII, cm.getEndpointParameterOptions(), Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).minWidth(20).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(this::getName),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT).with(this::getDescription),
                new Column().header("DEFAULT").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.NEWLINE)
                        .with(r -> r.getShortDefaultValue(40)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(BaseOptionModel::getShortJavaType))));
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

}
