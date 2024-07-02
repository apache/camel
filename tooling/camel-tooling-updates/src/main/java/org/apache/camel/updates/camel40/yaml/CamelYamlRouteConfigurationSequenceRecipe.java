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
package org.apache.camel.updates.camel40.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.camel.updates.AbstractCamelYamlVisitor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.format.IndentsVisitor;
import org.openrewrite.yaml.style.IndentsStyle;
import org.openrewrite.yaml.tree.Yaml;

import static org.openrewrite.Tree.randomId;

/**
 * Camel API changes requires several changes in YAML route definition. Route-configuration children sequence is
 * replaced with mappingEntry (with special migration of "on-exception")
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class CamelYamlRouteConfigurationSequenceRecipe extends Recipe {

    private static JsonPathMatcher MATCHER_ROUTE_CONFIGURATION = new JsonPathMatcher("$.route-configuration");
    private static JsonPathMatcher MATCHER_ROUTE_CONFIGURATION_ON_EXCEPTION
            = new JsonPathMatcher("$.route-configuration.on-exception");

    @Override
    public String getDisplayName() {
        return "Camel Yaml changes regarding route-configuration children";
    }

    @Override
    public String getDescription() {
        return "Camel YAML changes. route-configuration children sequence is replaced with  mappingEntry (with special migration of \"on-exception\").";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new AbstractCamelYamlVisitor() {

            private Yaml.Sequence sequenceToReplace;
            private boolean indentRegistered = false;

            @Override
            protected void clearLocalCache() {
                sequenceToReplace = null;
            }

            @Override
            public Yaml.Sequence doVisitSequence(Yaml.Sequence sequence, ExecutionContext context) {
                Yaml.Sequence s = super.doVisitSequence(sequence, context);

                //if there is a sequence in a route-configuration, it has to be replaced with mapping
                if (new JsonPathMatcher("$.route-configuration").matches(getCursor().getParent())) {
                    this.sequenceToReplace = s;
                }
                return s;
            }

            @Override
            public Yaml.Mapping.Entry doVisitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext context) {
                Yaml.Mapping.Entry e = super.doVisitMappingEntry(entry, context);

                //if current mapping contains an entry with sequence belonging to route-configuration, remove the sequence
                if (e.getValue() == sequenceToReplace) {
                    List<Yaml.Mapping.Entry> entries = new ArrayList<>();
                    for (Yaml.Sequence.Entry sEntry : sequenceToReplace.getEntries()) {

                        if (sEntry.getBlock() instanceof Yaml.Mapping) {
                            ((Yaml.Mapping) sEntry.getBlock()).getEntries().forEach(y -> {
                                //if entry is on-exception from the route-configuration sequence, it has to be handled differently
                                if ("on-exception".equals(y.getKey().getValue())) {
                                    Yaml.Sequence newSequence = sequenceToReplace.copyPaste();
                                    //keep only on-exception item
                                    List<Yaml.Sequence.Entry> filteredEntries = newSequence.getEntries().stream()
                                            .filter(se -> ((Yaml.Mapping) se.getBlock()).getEntries().stream()
                                                    .filter(me -> "on-exception".equals(me.getKey().getValue())).findFirst()
                                                    .isPresent())
                                            .collect(Collectors.toList());

                                    entries.add(y.withValue(newSequence.withEntries(filteredEntries)).withPrefix("\n"));
                                } else {
                                    entries.add(y.withPrefix("\n"));
                                }
                            });
                        }
                    }
                    Yaml.Mapping.Entry resultr = e.withValue(new Yaml.Mapping(
                            randomId(), sequenceToReplace.getMarkers(), sequenceToReplace.getOpeningBracketPrefix(), entries,
                            null, null));

                    if (!indentRegistered) {
                        indentRegistered = true;
                        //TODO might probably change indent in original file, may this happen?
                        doAfterVisit(new IndentsVisitor(new IndentsStyle(2), null));
                    }

                    return resultr;
                }
                return e;
            }
        };
    }

}
