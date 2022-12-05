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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.engine.DefaultLanguageResolver;
import org.apache.camel.main.util.SuggestSimilarHelper;
import org.apache.camel.spi.Language;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * Auto downloaded needed JARs when resolving languages.
 */
public final class DependencyDownloaderLanguageResolver extends DefaultLanguageResolver {

    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private CamelContext camelContext;
    private final DependencyDownloader downloader;

    public DependencyDownloaderLanguageResolver(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
    }

    @Override
    public Language resolveLanguage(String name, CamelContext context) {
        LanguageModel model = catalog.languageModel(name);
        if (model != null) {
            downloadLoader(model.getArtifactId(), model.getVersion());
            if ("csimple".equals(name)) {
                // need to include joor compiler also
                downloadLoader("camel-csimple-joor", model.getVersion());
            }
        }
        Language answer = super.resolveLanguage(name, context);
        if (answer == null) {
            List<String> suggestion = SuggestSimilarHelper.didYouMean(catalog.findDataFormatNames(), name);
            if (suggestion != null && !suggestion.isEmpty()) {
                String s = String.join(", ", suggestion);
                throw new IllegalArgumentException("Cannot find language with name: " + name + ". Did you mean: " + s);
            }
        }
        return answer;
    }

    private void downloadLoader(String artifactId, String version) {
        if (!downloader.alreadyOnClasspath("org.apache.camel", artifactId,
                version)) {
            downloader.downloadDependency("org.apache.camel", artifactId,
                    version);
        }
    }

}
