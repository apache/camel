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
package org.apache.camel.dsl.jbang.export.run;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.camel.dsl.jbang.export.DependencyResolver;
import org.apache.camel.dsl.jbang.export.ExportContext;
import org.apache.camel.dsl.jbang.export.common.PluginExporter;
import org.apache.camel.dsl.jbang.export.common.PluginRunCustomizer;
import org.apache.camel.dsl.jbang.export.common.Printer;

/**
 * What a run uses from its caller: what an export uses ({@link ExportContext}), as a run exports the project it runs,
 * and how the processes of the run are started, such as Maven and the JVM of the application.
 * <p>
 * A RunContext is not thread-safe: configure it, and use it for one run at a time.
 *
 * @since 4.24
 */
public class RunContext extends ExportContext {

    /**
     * Starts a process of a run.
     */
    @FunctionalInterface
    public interface ProcessStarter {
        Process start(ProcessBuilder builder) throws IOException;
    }

    private ProcessStarter processStarter = ProcessBuilder::start;
    private Path javaHome = Path.of(System.getProperty("java.home"));

    /**
     * Starts the given process of the run.
     */
    public Process startProcess(ProcessBuilder builder) throws IOException {
        return processStarter.start(builder);
    }

    /**
     * How the processes of the run are started (default {@link ProcessBuilder#start()}), such as a test that records
     * them instead.
     */
    public RunContext withProcessStarter(ProcessStarter processStarter) {
        this.processStarter = processStarter;
        return this;
    }

    public Path getJavaHome() {
        return javaHome;
    }

    /**
     * The Java installation whose java runs the application (default the one of this JVM).
     */
    public RunContext withJavaHome(Path javaHome) {
        this.javaHome = javaHome;
        return this;
    }

    @Override
    public RunContext withPrinter(Printer printer) {
        super.withPrinter(printer);
        return this;
    }

    @Override
    public RunContext withDependencyResolver(DependencyResolver dependencyResolver) {
        super.withDependencyResolver(dependencyResolver);
        return this;
    }

    @Override
    public RunContext withPluginExporters(Function<String, Collection<PluginExporter>> pluginExporters) {
        super.withPluginExporters(pluginExporters);
        return this;
    }

    @Override
    public RunContext withPluginRunCustomizers(Function<String, Collection<PluginRunCustomizer>> pluginRunCustomizers) {
        super.withPluginRunCustomizers(pluginRunCustomizers);
        return this;
    }

    @Override
    public RunContext withStartFailureListener(BiConsumer<Exception, List<String>> startFailureListener) {
        super.withStartFailureListener(startFailureListener);
        return this;
    }

    @Override
    public RunContext withConfirm(Predicate<String> confirm) {
        super.withConfirm(confirm);
        return this;
    }
}
