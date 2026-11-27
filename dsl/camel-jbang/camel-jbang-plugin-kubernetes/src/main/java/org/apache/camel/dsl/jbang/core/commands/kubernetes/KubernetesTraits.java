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
package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.util.Arrays;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitOptions;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitOptions.OptionInfo;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitOptions.TraitInfo;
import org.apache.camel.dsl.jbang.core.common.CamelTableColumns;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "traits",
                   description = "List the traits that configure the Kubernetes manifest, or the options of one trait",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel kubernetes traits",
                           "  camel kubernetes traits ingress",
                           "  camel kubernetes run * --trait ingress.enabled=true --trait ingress.host=my-app.example.com" })
public class KubernetesTraits extends CamelCommand {

    @Argument(description = "The trait to list the options of", paramLabel = "trait",
              completer = TraitNameCompletionCandidates.class)
    String trait;

    @Option(name = "json", hasValue = false, description = "Output in JSON Format")
    boolean jsonOutput;

    @Override
    public Integer doCall() throws Exception {
        if (trait == null || trait.isBlank()) {
            List<TraitInfo> traits = TraitOptions.traits();
            if (jsonOutput) {
                JsonOutput.printList(printer(), traits.stream().map(KubernetesTraits::toJson).toList());
            } else {
                printTraits(traits);
            }
            return CliExitCode.OK;
        }
        TraitInfo info = TraitOptions.trait(trait)
                .orElseThrow(() -> CliException.notFound(
                        "Unknown trait: " + trait + " (camel kubernetes traits lists the traits)"));
        if (jsonOutput) {
            JsonOutput.print(printer(), toJson(info));
        } else {
            printOptions(info);
        }
        return CliExitCode.OK;
    }

    private void printTraits(List<TraitInfo> traits) {
        int nameWidth = CamelTableColumns.measure("TRAIT", CamelTableColumns.NAME_MAX, traits, TraitInfo::name);
        int descWidth = CamelTableColumns.lastColumnWidth(
                TerminalWidthHelper.getTerminalWidth(), TerminalWidthHelper.noBorderOverhead(2), nameWidth);
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, traits, Arrays.asList(
                new Column().header("TRAIT").dataAlign(HorizontalAlign.LEFT).with(TraitInfo::name),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(descWidth, OverflowBehaviour.NEWLINE).with(TraitInfo::description))));
        printer().println();
        printer().println("Show the options of a trait with: camel kubernetes traits <trait>");
    }

    private void printOptions(TraitInfo info) {
        printer().println(info.name() + ": " + info.description());
        printer().println();
        List<OptionInfo> options = info.options();
        int nameWidth = CamelTableColumns.measure("OPTION", CamelTableColumns.NAME_MAX, options, o -> option(info, o));
        int typeWidth = CamelTableColumns.measure("TYPE", 10, options, OptionInfo::type);
        int descWidth = CamelTableColumns.lastColumnWidth(
                TerminalWidthHelper.getTerminalWidth(), TerminalWidthHelper.noBorderOverhead(3), nameWidth, typeWidth);
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, options, Arrays.asList(
                new Column().header("OPTION").dataAlign(HorizontalAlign.LEFT).with(o -> option(info, o)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(OptionInfo::type),
                new Column().header("DESCRIPTION").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(descWidth, OverflowBehaviour.NEWLINE).with(KubernetesTraits::describe))));
        printer().println();
        printer().println("Set an option with: --trait " + info.name() + ".<option>=<value>");
    }

    private static String option(TraitInfo info, OptionInfo option) {
        return info.name() + "." + option.name();
    }

    private static String describe(OptionInfo option) {
        // the description of most choices already names their values
        if (option.values().isEmpty() || option.values().stream().allMatch(v -> option.description().contains(v))) {
            return option.description();
        }
        return option.description() + " Values: " + String.join(", ", option.values()) + ".";
    }

    private static JsonObject toJson(TraitInfo info) {
        JsonObject jo = new JsonObject();
        jo.put("name", info.name());
        jo.put("description", info.description());
        JsonArray options = new JsonArray();
        for (OptionInfo option : info.options()) {
            JsonObject o = new JsonObject();
            o.put("name", option.name());
            o.put("type", option.type());
            o.put("description", option.description());
            if (!option.values().isEmpty()) {
                o.put("values", new JsonArray(option.values()));
            }
            options.add(o);
        }
        jo.put("options", options);
        return jo;
    }
}
