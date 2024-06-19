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
package org.apache.camel.updates;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;

public class CamelTestUtil {

    /**
     * Enumeration of Camel version, with precise versions of dependencies and the name of the recipe
     */
    public enum CamelVersion {
        v3_18(3, 18, 6),
        v4_0(4, 0, 3),
        v4_4(4, 4, 2);

        private int major;
        private int minor;
        private int patch;

        CamelVersion(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        public String getVersion() {
            return getMajorMinor() + "." + patch;
        }

        public String getMajorMinor() {
            return major + "." + minor;
        }

        public String getYamlFile() {
            return "/META-INF/rewrite/" + getMajorMinor() + ".yaml";
        }

        public String getRecipe() {
            return "org.apache.camel.updates.camel" + major + minor + ".CamelMigrationRecipe";
        }
    }

    private static RecipeSpec recipe(RecipeSpec spec, CamelVersion to) {
        return spec.recipeFromResource(to.getRecipe());
    }

    public static RecipeSpec recipe(RecipeSpec spec, CamelVersion to, String... activerecipes) {
        if (activerecipes == null || activerecipes.length == 0) {
            return spec.recipeFromResource(to.getYamlFile(), to.getRecipe());
        }
        return spec.recipeFromResource(to.getYamlFile(), activerecipes);
    }

    public static Parser.Builder parserFromClasspath(CamelVersion from, String... classpath) {
        List<String> resources = Arrays.stream(classpath).map(cl -> {
            if (cl.startsWith("camel-")) {
                return cl + "-" + from.getVersion();
            }
            return cl;
        }).collect(Collectors.toList());

        return JavaParser.fromJavaVersion().logCompilationWarningsAndErrors(true)
                .classpathFromResources(new InMemoryExecutionContext(), resources.toArray(new String[resources.size()]));
    }

}
