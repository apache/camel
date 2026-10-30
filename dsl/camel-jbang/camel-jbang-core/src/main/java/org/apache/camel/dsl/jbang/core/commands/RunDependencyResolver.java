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

import java.util.Properties;

import org.apache.camel.dsl.jbang.export.DependencyResolver;
import org.apache.camel.dsl.jbang.export.ExportRequest;

/**
 * Resolves the dependencies of an export by booting the integration silently, the same way as camel run does, which
 * writes the run settings that the exporter reads.
 */
public class RunDependencyResolver implements DependencyResolver {

    private final CamelJBangMain main;

    public RunDependencyResolver() {
        this(new CamelJBangMain());
    }

    public RunDependencyResolver(CamelJBangMain main) {
        this.main = main;
    }

    @Override
    public int resolve(ExportRequest request) throws Exception {
        Run run = new Run(main);
        // need to declare the profile to use for run
        run.exportBaseDir = request.exportBaseDir;
        run.workDir = request.workDir;
        run.dependencies = request.dependencies;
        run.files = request.files;
        run.name = request.name;
        run.serverOptions.port = request.port;
        run.serverOptions.managementPort = request.managementPort;
        run.excludes = request.excludes;
        run.openapi = request.openapi;
        run.serverOptions.observe = request.observe;
        run.serverOptions.console = request.console;
        run.mavenResolver = MavenResolverMixin.of(new Properties(), request.mavenResolver);
        run.packageScanJars = request.packageScanJars;
        run.runtime = request.runtime;
        run.camelVersion = request.camelVersion;
        run.camelSpringBootVersion = request.camelSpringBootVersion;
        run.quarkusPlatform = QuarkusPlatformMixin.of(new Properties(), request.quarkusPlatform);
        run.springBootVersion = request.springBootVersion;
        run.kameletsVersion = request.kameletsVersion;
        run.javaVersion = request.javaVersion;
        run.localKameletDir = request.localKameletDir;
        run.resourceDirs = request.resourceDirs;
        run.ignoreLoadingError = request.ignoreLoadingError;
        run.lazyBean = request.lazyBean;
        run.property = request.applicationProperties;
        run.verbose = request.verbose;
        run.loggingOptions.logging = request.logging;
        return run.runExport(request.ignoreLoadingError);
    }
}
