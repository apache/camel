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
import java.util.List;
import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.run.common.PluginExporter;
import org.apache.camel.dsl.jbang.run.common.PluginRunCustomizer;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.main.KameletMain;

/**
 * Plugins that link with the CLI that loads them, while their exporter or run customizer does not, such as plugins
 * built for another Camel version whose exporter refers to a class that has moved: the JVM links a class when it is
 * first used, so creating the plugin works, and asking it for its exporter or run customizer fails.
 */
public final class UnlinkedContributionFakePlugins {

    private UnlinkedContributionFakePlugins() {
    }

    /**
     * Its run customizer links, its exporter does not.
     */
    public static class UnlinkedExporterPlugin implements Plugin {

        @Override
        public void customize(CommandTree commandLine, CamelJBangMain main) {
            // no-op
        }

        @Override
        public Optional<PluginRunCustomizer> getRunCustomizer() {
            return Optional.of((main, files) -> {
            });
        }

        @Override
        public Optional<PluginExporter> getExporter() {
            return Optional.of(new UnlinkedExporter());
        }
    }

    /**
     * Its run customizer does not link.
     */
    public static class UnlinkedRunCustomizerPlugin implements Plugin {

        @Override
        public void customize(CommandTree commandLine, CamelJBangMain main) {
            // no-op
        }

        @Override
        public Optional<PluginRunCustomizer> getRunCustomizer() {
            return Optional.of(new UnlinkedRunCustomizer());
        }
    }

    static class UnlinkedExporter implements PluginExporter {

        static {
            // as the JVM fails when the class links: the error, not wrapped
            if (Boolean.TRUE) {
                throw new NoClassDefFoundError("org/apache/camel/dsl/jbang/core/common/Printer");
            }
        }

        @Override
        public void addSourceFiles(Path buildDir, String packageName, Printer printer) {
            // no-op
        }
    }

    static class UnlinkedRunCustomizer implements PluginRunCustomizer {

        static {
            // as the JVM fails when the class links: the error, not wrapped
            if (Boolean.TRUE) {
                throw new NoClassDefFoundError("org/apache/camel/dsl/jbang/core/common/Printer");
            }
        }

        @Override
        public void beforeRun(KameletMain main, List<String> files) {
            // no-op
        }
    }
}
