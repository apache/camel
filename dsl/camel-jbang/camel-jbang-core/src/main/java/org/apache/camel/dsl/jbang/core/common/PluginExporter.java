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

package org.apache.camel.dsl.jbang.core.common;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

/**
 * Plugin that is able to participate in a project export. The plugin is able to add properties and dependencies to the
 * generated project.
 */
public interface PluginExporter {

    /**
     * Provide additional build properties.
     *
     * @return build properties to add to the exported project.
     */
    default Properties getBuildProperties() {
        return new Properties();
    }

    /**
     * Provide additional dependencies in Maven GAV format (groupId:artifactId:version).
     *
     * @param  runtimeType Type of runtime (defined by parameter --runtime)
     * @return             set of Maven GAVs.
     */
    default Set<String> getDependencies(RuntimeType runtimeType) {
        return Collections.emptySet();
    }

    /**
     * Checks if the plugin exporter should perform. Implementations may add explicit logic to determine if the plugin
     * exporter should be skipped.
     *
     * @return flag indicating that the plugin is eligible to perform.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Add plugin specific source files to the exported project.
     */
    void addSourceFiles(Path buildDir, String packageName, Printer printer) throws Exception;
}
