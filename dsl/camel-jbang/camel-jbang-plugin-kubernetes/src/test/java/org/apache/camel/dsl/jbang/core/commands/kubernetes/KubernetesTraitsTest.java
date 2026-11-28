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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitOptions;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitOptions.OptionInfo;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitOptions.TraitInfo;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-22733: camel kubernetes traits lists the traits and their options from the trait model.
 */
class KubernetesTraitsTest {

    private final StringPrinter printer = new StringPrinter();

    @Test
    void listsTheTraits() throws Exception {
        Assertions.assertEquals(0, command().doCall());

        String out = printer.getOutput();
        for (String trait : List.of("container", "cronjob", "ingress", "jolokia", "knative-service", "mount", "route",
                "service")) {
            Assertions.assertTrue(out.contains(trait), trait + " is not listed:\n" + out);
        }
        // not traits of their own: addons, and service-binding that no trait applies
        Assertions.assertFalse(out.contains("service-binding"), out);
        Assertions.assertFalse(out.contains("addons"), out);
        Assertions.assertTrue(out.contains("camel kubernetes traits <trait>"), out);
    }

    @Test
    void listsTheOptionsOfATrait() throws Exception {
        KubernetesTraits command = command();
        command.trait = "ingress";
        Assertions.assertEquals(0, command.doCall());

        String out = printer.getOutput();
        Assertions.assertTrue(out.startsWith("ingress: An Ingress"), out);
        Assertions.assertTrue(out.contains("ingress.host"), out);
        Assertions.assertTrue(out.contains("ingress.tls-secret-name"), out);
        Assertions.assertTrue(out.contains("ingress.path-type"), out);
        // its description names the values of the choice already
        Assertions.assertFalse(out.contains("Values:"), out);
    }

    @Test
    void aChoiceShowsItsValues() throws Exception {
        KubernetesTraits command = command();
        command.trait = "route";
        Assertions.assertEquals(0, command.doCall());

        // the description of tls-insecure-edge-termination-policy does not name its values
        String out = printer.getOutput().replaceAll("\\s+", " ");
        Assertions.assertTrue(out.contains("Values: None, Allow, Redirect."), out);
    }

    @Test
    void theOptionsOfATraitAsJson() throws Exception {
        KubernetesTraits command = command();
        command.trait = "ingress";
        command.jsonOutput = true;
        Assertions.assertEquals(0, command.doCall());

        JsonObject json = (JsonObject) Jsoner.deserialize(printer.getOutput());
        Assertions.assertEquals("ingress", json.getString("name"));
        JsonObject pathType = ((JsonArray) json.get("options")).stream().map(JsonObject.class::cast)
                .filter(o -> "path-type".equals(o.getString("name"))).findFirst().orElseThrow();
        Assertions.assertEquals("string", pathType.getString("type"));
        Assertions.assertEquals(List.of("Exact", "Prefix", "ImplementationSpecific"), pathType.get("values"));
    }

    @Test
    void theTraitsAsJson() throws Exception {
        KubernetesTraits command = command();
        command.jsonOutput = true;
        Assertions.assertEquals(0, command.doCall());

        JsonArray json = (JsonArray) Jsoner.deserialize(printer.getOutput());
        Assertions.assertEquals(TraitOptions.traits().size(), json.size());
    }

    @Test
    void anUnknownTraitIsNotFound() {
        KubernetesTraits command = command();
        command.trait = "nosuch";
        CliException e = Assertions.assertThrows(CliException.class, command::doCall);
        Assertions.assertEquals(CliExitCode.NOT_FOUND, e.getExitCode());
        Assertions.assertTrue(e.getMessage().contains("Unknown trait: nosuch"), e.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyListedOptionIsReadByTheTraitOption() {
        // the names shown are the names --trait takes: each option given with --trait ends up in the trait model
        List<String> missing = new ArrayList<>();
        for (TraitInfo trait : TraitOptions.traits()) {
            for (OptionInfo option : trait.options()) {
                String expression = trait.name() + "." + option.name() + "=" + sample(option);
                Traits traits = TraitHelper.parseTraits(new String[] { expression });
                Map<String, Object> model = KubernetesHelper.json().convertValue(traits, Map.class);
                Map<String, Object> config = (Map<String, Object>) model.get(trait.name());
                if (config == null || !config.containsKey(StringHelper.dashToCamelCase(option.name()))
                        && config.keySet().stream().noneMatch(k -> StringHelper.camelCaseToDash(k).equals(option.name()))) {
                    missing.add(expression);
                }
            }
        }
        Assertions.assertEquals(List.of(), missing, "--trait does not set these options");
    }

    @Test
    void theCompletionOfTraitHasEveryOption() {
        List<String> candidates = new ArrayList<>();
        new TraitCompletionCandidates().forEach(candidates::add);
        Assertions.assertTrue(candidates.contains("container.port="), candidates.toString());
        Assertions.assertTrue(candidates.contains("ingress.path-type="), candidates.toString());
        Assertions.assertEquals(TraitOptions.completionCandidates(), candidates);
    }

    private static String sample(OptionInfo option) {
        if (!option.values().isEmpty()) {
            return option.values().get(0);
        }
        return switch (option.type()) {
            case "bool" -> "true";
            case "int" -> "1";
            case "map" -> "key=value";
            default -> "value";
        };
    }

    private KubernetesTraits command() {
        return new CamelJBangMain().withPrinter(printer).command(new KubernetesTraits());
    }
}
