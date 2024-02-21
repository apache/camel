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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultResourceLoader;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.StringHelper;

public class DependencyDownloaderResourceLoader extends DefaultResourceLoader {

    private final DependencyDownloader downloader;
    private final String sourceDir;

    public DependencyDownloaderResourceLoader(CamelContext camelContext, String sourceDir) {
        super(camelContext);
        this.sourceDir = sourceDir;
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
        if (sourceDir != null) {
            boolean exists = answer != null && answer.exists();
            // if not found then we need to look again inside the source-dir which we can do
            // for file and classpath resources
            if (!exists && ("classpath".equals(scheme) || "file".equals(scheme))) {
                String path = StringHelper.after(uri, ":");
                // strip leading double slash
                if (path.startsWith("//")) {
                    path = path.substring(2);
                }
                // force to load from file system when using source-dir
                uri = "file" + ":" + sourceDir + File.separator + path;
                answer = super.resolveResource(uri);
            }
        }
        return answer;
    }

}
