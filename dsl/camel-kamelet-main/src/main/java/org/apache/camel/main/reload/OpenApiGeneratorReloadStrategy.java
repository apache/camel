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
package org.apache.camel.main.reload;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.CamelContext;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.support.FileWatcherResourceReloadStrategy;
import org.apache.camel.support.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When using --open-api in --dev mode then we need to watch the openapi spec file if its changed, and then regenerate
 * the route file, which is then reloaded as a regular route.
 */
public class OpenApiGeneratorReloadStrategy extends FileWatcherResourceReloadStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiGeneratorReloadStrategy.class);

    // write to jbang generated file so it can be reloaded
    private static final String OPENAPI_GENERATED_FILE = ".camel-jbang/generated-openapi.yaml";

    private final File openapi;
    private Method method;

    public OpenApiGeneratorReloadStrategy(File openapi) {
        String parent = openapi.getParent();
        if (parent == null) {
            parent = ".";
        }
        setFolder(parent);
        // need to adjust file to be what file watcher uses when matching
        Path dir = new File(parent).toPath();
        this.openapi = dir.resolve(openapi.toPath()).toFile();
        setFileFilter(this.openapi::equals);
        setResourceReload((name, resource) -> {
            if (!openapi.exists() && !openapi.isFile()) {
                return;
            }

            LOG.info("Generating open-api rest-dsl from: {}", openapi);
            try {
                String out = (String) ObjectHelper.invokeMethodSafe(method, null, getCamelContext(), openapi);
                Files.write(Paths.get(OPENAPI_GENERATED_FILE), out.getBytes());
            } catch (Exception e) {
                LOG.warn("Error generating open-api rest-dsl due: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        DependencyDownloader downloader = getCamelContext().hasService(DependencyDownloader.class);
        // these are extra dependencies used in special use-case so download as hidden
        downloader.downloadHiddenDependency("org.apache.camel", "camel-openapi-rest-dsl-generator",
                getCamelContext().getVersion());

        // the generator is invoked via reflection
        Class<?> clazz = getCamelContext().getClassResolver()
                .resolveMandatoryClass("org.apache.camel.generator.openapi.RestDslGenerator");
        method = clazz.getDeclaredMethod("generateToYaml", CamelContext.class, File.class);
    }
}
