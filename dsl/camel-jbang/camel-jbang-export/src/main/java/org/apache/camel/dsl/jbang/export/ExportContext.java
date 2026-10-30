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
package org.apache.camel.dsl.jbang.export;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.camel.dsl.jbang.export.common.PluginExporter;
import org.apache.camel.dsl.jbang.export.common.PluginRunCustomizer;
import org.apache.camel.dsl.jbang.export.common.Printer;

/**
 * What an export uses from its caller: where it prints, how it resolves the dependencies, the exporters of the active
 * plugins, and how it asks for confirmation (such as before it deletes the files of the export directory).
 * <p>
 * An ExportContext is not thread-safe: configure it, and use it for one export at a time.
 *
 * @since 4.24
 */
public class ExportContext {

    private Printer printer = new Printer.SystemOutPrinter();
    private DependencyResolver dependencyResolver;
    private Function<String, Collection<PluginExporter>> pluginExporters = repos -> List.of();
    private Function<String, Collection<PluginRunCustomizer>> pluginRunCustomizers = repos -> List.of();
    private Predicate<String> confirm = message -> true;

    public Printer getPrinter() {
        return printer;
    }

    /**
     * Where the export prints (default the console).
     */
    public ExportContext withPrinter(Printer printer) {
        this.printer = printer;
        return this;
    }

    /**
     * The resolver of the dependencies; when none is set, the one registered with the {@link ServiceLoader} is used.
     */
    public DependencyResolver getDependencyResolver() {
        if (dependencyResolver == null) {
            Iterator<DependencyResolver> it
                    = ServiceLoader.load(DependencyResolver.class, ExportContext.class.getClassLoader()).iterator();
            if (!it.hasNext()) {
                throw new IllegalStateException(
                        "No " + DependencyResolver.class.getName()
                                                + " found: add camel-jbang-core to the classpath, or set a resolver on the "
                                                + ExportContext.class.getSimpleName());
            }
            dependencyResolver = it.next();
        }
        return dependencyResolver;
    }

    public ExportContext withDependencyResolver(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
        return this;
    }

    /**
     * The exporters of the active plugins, for the given additional Maven repositories (can be null).
     */
    public Collection<PluginExporter> getPluginExporters(String repos) {
        return pluginExporters.apply(repos);
    }

    /**
     * How to find the exporters of the active plugins, for the given additional Maven repositories (default none).
     */
    public ExportContext withPluginExporters(Function<String, Collection<PluginExporter>> pluginExporters) {
        this.pluginExporters = pluginExporters;
        return this;
    }

    /**
     * The run customizers of the active plugins, for the given additional Maven repositories (can be null).
     */
    public Collection<PluginRunCustomizer> getPluginRunCustomizers(String repos) {
        return pluginRunCustomizers.apply(repos);
    }

    /**
     * How to find the run customizers of the active plugins, which customize the silent run of the export, for the
     * given additional Maven repositories (default none).
     */
    public ExportContext withPluginRunCustomizers(Function<String, Collection<PluginRunCustomizer>> pluginRunCustomizers) {
        this.pluginRunCustomizers = pluginRunCustomizers;
        return this;
    }

    /**
     * Whether the given operation, such as cleaning the export directory, may proceed.
     */
    public boolean confirm(String message) {
        return confirm.test(message);
    }

    /**
     * How to ask for confirmation of an operation (default it proceeds, as with --yes).
     */
    public ExportContext withConfirm(Predicate<String> confirm) {
        this.confirm = confirm;
        return this;
    }
}
