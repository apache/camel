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
package org.apache.camel.updates.customRecipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

/**
 * Replaces prefix with the new one and changes the suffix tp start with lower case
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ChangePropertyKeyWithCaseChange extends Recipe {

    @Option(displayName = "Old property key",
            description = "The property key to rename.")
    String oldPropertyKey;

    @Option(displayName = "New prefix before any group",
            description = "The prefix to be replaced with.")
    String newPrefix;

    @Override
    public String getDisplayName() {
        return "Change prefix of property with Camel case";
    }

    @Override
    public String getDescription() {
        return "Change prefix of property with Camel case";
    }

    @Override
    public PropertiesVisitor<ExecutionContext> getVisitor() {
        return new PropertiesVisitor<>() {
            @Override
            public Properties visitEntry(Properties.Entry entry, ExecutionContext p) {
                if (entry.getKey().matches(oldPropertyKey)) {
                    entry = entry.withKey(getKey(entry))
                            .withPrefix(entry.getPrefix());
                }
                return super.visitEntry(entry, p);
            }

            //replace key
            private String getKey(Properties.Entry entry) {
                return newPrefix + entry.getKey().replaceFirst(oldPropertyKey, "$1").substring(0, 1).toLowerCase()
                       + entry.getKey().replaceFirst(oldPropertyKey, "$1").substring(1);

            }
        };
    }
}
