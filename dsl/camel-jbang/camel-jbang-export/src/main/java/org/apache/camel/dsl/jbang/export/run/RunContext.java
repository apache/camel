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
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import org.apache.camel.dsl.jbang.export.DependencyResolver;
import org.apache.camel.dsl.jbang.export.ExportContext;
import org.apache.camel.dsl.jbang.export.common.Printer;
import org.apache.camel.main.KameletMain;

/**
 * What a run uses from its caller: what an export uses ({@link ExportContext}), as a run exports the project it runs,
 * how the processes of the run are started, such as Maven and the JVM of the application, and what the caller adds to a
 * run in this JVM (the jbang runtime), such as its logging.
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

    /**
     * Called when a run in this JVM is prepared: the work directory is created and the application properties are
     * loaded, and nothing is printed or logged yet.
     */
    @FunctionalInterface
    public interface PreparedListener {

        /**
         * @param request    the request, which the application properties configured (not with --empty, where they only
         *                   configure the logging); files added to it run as well (such as camel run --code)
         * @param properties the application properties, the profile overriding application.properties; null when none
         *                   of the files exists, and with a run that transforms messages
         * @param mcpStdio   whether the run serves the MCP tools over stdio, where nothing else may write to the
         *                   standard output
         */
        void prepared(RunRequest request, Properties properties, boolean mcpStdio) throws Exception;
    }

    private ProcessStarter processStarter = ProcessBuilder::start;
    private Path javaHome = Path.of(System.getProperty("java.home"));
    private ToIntFunction<Throwable> failureExitCode = failure -> 1;
    private PreparedListener preparedListener = (request, properties, mcpStdio) -> {
    };
    private BiConsumer<Throwable, List<String>> reloadFailureListener = (failure, files) -> {
    };
    private Consumer<KameletMain> initialProperties = main -> {
    };
    private RunPipeline.FileLoader clipboard = file -> file;
    private Runnable dataDirWithoutSourceDir = () -> {
    };

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

    /**
     * The exit code of a run in this JVM that failed with the given exception, as the previous log of the integration
     * records it.
     */
    public int failureExitCode(Throwable failure) {
        return failureExitCode.applyAsInt(failure);
    }

    /**
     * The exit code of a run in this JVM that failed (default 1), such as the one camel run exits with for the
     * exception.
     */
    public RunContext withFailureExitCode(ToIntFunction<Throwable> failureExitCode) {
        this.failureExitCode = failureExitCode;
        return this;
    }

    /**
     * Called when a run in this JVM is prepared (see {@link PreparedListener}).
     */
    public void onPrepared(RunRequest request, Properties properties, boolean mcpStdio) throws Exception {
        preparedListener.prepared(request, properties, mcpStdio);
    }

    /**
     * What the caller does when a run in this JVM is prepared (default nothing), such as configuring its logging with
     * the application properties, as the engine configures no logging. The printer of the context is read after it.
     */
    public RunContext withPreparedListener(PreparedListener preparedListener) {
        this.preparedListener = preparedListener;
        return this;
    }

    /**
     * Called when a run in this JVM fails to reload a route file in dev mode, with the failure and the file.
     */
    public void onReloadFailure(Throwable failure, List<String> files) {
        reloadFailureListener.accept(failure, files);
    }

    /**
     * What is reported when a run in this JVM fails to reload a route file in dev mode (default nothing), as
     * {@link #withStartFailureListener(BiConsumer)} on a failed start.
     */
    public RunContext withReloadFailureListener(BiConsumer<Throwable, List<String>> reloadFailureListener) {
        this.reloadFailureListener = reloadFailureListener;
        return this;
    }

    public Consumer<KameletMain> getInitialProperties() {
        return initialProperties;
    }

    /**
     * Adds initial properties of the caller to the Camel Main of a run in this JVM, after the ones of the kind of run
     * (default none).
     */
    public RunContext withInitialProperties(Consumer<KameletMain> initialProperties) {
        this.initialProperties = initialProperties;
        return this;
    }

    public RunPipeline.FileLoader getClipboard() {
        return clipboard;
    }

    /**
     * Loads a file starting with clipboard that does not exist, in a run in this JVM (default none).
     */
    public RunContext withClipboard(RunPipeline.FileLoader clipboard) {
        this.clipboard = clipboard;
        return this;
    }

    public Runnable getDataDirWithoutSourceDir() {
        return dataDirWithoutSourceDir;
    }

    /**
     * Warns that the data directory has no effect without a source directory, in dev mode (default nothing).
     */
    public RunContext withDataDirWithoutSourceDir(Runnable warning) {
        this.dataDirWithoutSourceDir = warning;
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
    public RunContext withPlugins(Function<String, ActivePlugins> plugins) {
        super.withPlugins(plugins);
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
