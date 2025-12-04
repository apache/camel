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

import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine;

@FunctionalInterface
public interface Plugin {

    /**
     * Customize given command line adding sub-commands in particular.
     *
     * @param commandLine the command line to adjust.
     * @param main        the current JBang main.
     */
    void customize(CommandLine commandLine, CamelJBangMain main);

    /**
     * The plugin may provide an optional project exporter implementation that is able to participate in an export
     * performed by Camel JBang. Project exporter implementations may add properties and dependencies to the generated
     * export.
     *
     * @return the plugin specific exporter implementation, otherwise empty
     */
    default Optional<PluginExporter> getExporter() {
        return Optional.empty();
    }
}
