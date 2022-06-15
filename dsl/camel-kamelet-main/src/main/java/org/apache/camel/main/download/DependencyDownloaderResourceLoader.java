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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultResourceLoader;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.StringHelper;

public class DependencyDownloaderResourceLoader extends DefaultResourceLoader {

    private final DependencyDownloader downloader;

    public DependencyDownloaderResourceLoader(CamelContext camelContext) {
        super(camelContext);
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
        return super.resolveResource(uri);
    }

}
