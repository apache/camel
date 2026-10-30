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
package org.apache.camel.dsl.jbang.core.commands;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.util.ReflectionHelper;

public final class RunHelper {

    private RunHelper() {
    }

    public static boolean isInCamelCatalog(String artifactId) {
        return isInCamelCatalog(new DefaultCamelCatalog(), artifactId);
    }

    public static boolean isInCamelCatalog(CamelCatalog catalog, String artifactId) {
        for (String n : catalog.findComponentNames()) {
            String a = catalog.componentModel(n).getArtifactId();
            if (artifactId.equals(a)) {
                return true;
            }
        }
        for (String n : catalog.findLanguageNames()) {
            String a = catalog.languageModel(n).getArtifactId();
            if (artifactId.equals(a)) {
                return true;
            }
        }
        for (String n : catalog.findDataFormatNames()) {
            String a = catalog.dataFormatModel(n).getArtifactId();
            if (artifactId.equals(a)) {
                return true;
            }
        }
        for (String n : catalog.findOtherNames()) {
            String a = catalog.otherModel(n).getArtifactId();
            if (artifactId.equals(a)) {
                return true;
            }
        }
        return false;
    }

    public static void doWithFields(Class<?> clazz, ReflectionHelper.FieldCallback fc) throws IllegalArgumentException {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                fc.doWith(field);
            } catch (IllegalAccessException ex) {
                // ignore
            }
        }
    }

    /**
     * When using camel run . or camel export . then dot should include all the files in the current folder. The files
     * of the current folder are named without a leading ./ (orders.camel.yaml), so they match the reload pattern in dev
     * mode.
     */
    public static void dirToFiles(String dir, List<String> files) {
        ExportHelper.dirToFiles(dir, files);
    }

    /**
     * Adds camel to the start of the list of commands to make it possible to run camel-jbang using a spawned process
     * (to run in background).
     */
    public static void addCamelCLICommand(List<String> cmds) {
        cmds.addAll(0, LauncherHelper.getCamelCommand());
    }
}
