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
package org.apache.camel.main.download;

import java.io.File;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultResourceLoader;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.StringHelper;

public class DependencyDownloaderResourceLoader extends DefaultResourceLoader {

    private final DependencyDownloader downloader;
    private final String sourceDir;
    private final List<String> fallbackDirs;

    public DependencyDownloaderResourceLoader(CamelContext camelContext, String sourceDir) {
        this(camelContext, sourceDir, List.of());
    }

    /**
     * @param sourceDir    the --source-dir, when used: a classpath: or file: resource not found is looked up there
     * @param fallbackDirs the directories of the route files (CAMEL-24852): without a source-dir, a classpath: or file:
     *                     resource not found is looked up in them, so resource:classpath:mapping.groovy finds the
     *                     script next to the route the way it does in an exported project
     */
    public DependencyDownloaderResourceLoader(CamelContext camelContext, String sourceDir, List<String> fallbackDirs) {
        super(camelContext);
        this.sourceDir = sourceDir;
        this.fallbackDirs = fallbackDirs != null ? fallbackDirs : List.of();
        this.downloader = camelContext.hasService(DependencyDownloader.class);
    }

    @Override
    public Resource resolveResource(String uri) {
        String scheme = StringHelper.before(uri, ":");
        if ("github".equals(scheme) || "gist".equals(scheme)) {
            if (!hasResourceResolver(scheme)) {
                // need to download github resolver
                if (!downloader.alreadyOnClasspath(
                        "org.apache.camel", "camel-resourceresolver-github",
                        getCamelContext().getVersion())) {
                    downloader.downloadDependency("org.apache.camel",
                            "camel-resourceresolver-github",
                            getCamelContext().getVersion());
                }
            }
        }
        Resource answer = super.resolveResource(uri);
        // the scheme is checked before exists(): only a classpath: or file: resource is looked up next to the
        // routes, and on an http: resource exists() is a GET (rest-openapi read its specification twice)
        if (("classpath".equals(scheme) || "file".equals(scheme)) && (answer == null || !answer.exists())) {
            String path = StringHelper.after(uri, ":");
            // strip leading double slash
            if (path.startsWith("//")) {
                path = path.substring(2);
            }
            if (sourceDir != null) {
                // if not found then we need to look again inside the source-dir which we can do for file and
                // classpath resources; only when the file is there: a resource that exists nowhere keeps the
                // original answer, so ?optional=true still means optional and an error names it as written
                // (CAMEL-24865: classpath:camel-joor.properties?optional=true became a file that did not exist)
                Resource candidate = super.resolveResource("file:" + sourceDir + File.separator + path);
                if (candidate != null && candidate.exists()) {
                    answer = candidate;
                }
            } else {
                // the files next to the routes: the first directory that has it wins, else the original answer
                // (so the error names the resource as written)
                for (String dir : fallbackDirs) {
                    Resource candidate = super.resolveResource("file:" + dir + File.separator + path);
                    if (candidate != null && candidate.exists()) {
                        answer = candidate;
                        break;
                    }
                }
            }
        }
        return answer;
    }

}
