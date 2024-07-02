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
package org.apache.camel.updates.camel41;

import java.util.List;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.camel.updates.AbstractCamelYamlVisitor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.tree.Yaml;

/**
 * Fixes following yaml change.
 *
 * When creating beans from an inlined script. The name of the script was defined in type, but has been changed to a new
 * scriptLanguage attribute. And beanType has been removed as you must use type instead.
 *
 * The old syntax:
 *
 * <pre>
 *     - beans:
 *       - name: "myClient"
 *         beanType: "com.foo.MyBean"
 *         type: "groovy"
 *         script: |
 *           # groovy script here
 * </pre>
 *
 * should be changed to:
 *
 * <pre>
 *   - beans:
 *     - name: "myClient"
 *       type: "com.foo.MyBean"
 *       scriptLanguage: "groovy"
 *       script: |
 *         # groovy script here
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class YamlDslRecipe extends Recipe {

    private static JsonPathMatcher MATCHER_WITHOUT_ROUTE = new JsonPathMatcher("$.beans");

    @Override
    public String getDisplayName() {
        return "Changes for creation of inlined beans.";
    }

    @Override
    public String getDescription() {
        return "If inlined bean is created, parameters `type` and `beanType` has bean changed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new AbstractCamelYamlVisitor() {

            @Override
            protected void clearLocalCache() {
                //nothing to do
            }

            @Override
            public Yaml.Mapping.Entry doVisitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.doVisitMappingEntry(entry, ctx);

                //getCursor().getParent() is mapping with 4 entries -> I can check the siblings
                //parent(4) is beans
                Cursor parent4 = getCursor().getParent(4);
                //check that this mapping entry is currently under "beans" sequence
                //parent(2) has to be mapping (represents the bean)
                if (parent4 != null && parent4.getParent() != null && MATCHER_WITHOUT_ROUTE.matches(parent4)
                        && getCursor().getParent().getValue() instanceof Yaml.Mapping) {
                    //get entries
                    Yaml.Mapping m = getCursor().getParent().getValue();
                    List<Yaml.Mapping.Entry> entries = m.getEntries();
                    Optional<Yaml.Mapping.Entry> typeEntry
                            = entries.stream().filter(me -> "type".equals(me.getKey().getValue())).findAny();
                    Optional<Yaml.Mapping.Entry> beanTypeEntry
                            = entries.stream().filter(me -> "beanType".equals(me.getKey().getValue())).findAny();

                    if (typeEntry.isPresent() && typeEntry.get().getValue() instanceof Yaml.Scalar
                            && !((Yaml.Scalar) typeEntry.get().getValue()).getValue().isEmpty()
                            && beanTypeEntry.isPresent() && beanTypeEntry.get().getValue() instanceof Yaml.Scalar
                            && !((Yaml.Scalar) beanTypeEntry.get().getValue()).getValue().isEmpty()) {

                        //modify the current entry
                        if ("type".equals(e.getKey().getValue())) {
                            return e.withKey(((Yaml.Scalar) entry.getKey().copyPaste()).withValue("scriptLanguage"));
                        }
                        if ("beanType".equals(e.getKey().getValue())) {
                            return e.withKey(((Yaml.Scalar) entry.getKey().copyPaste()).withValue("type"));
                        }
                    }
                }

                return e;
            }
        };
    }

}
