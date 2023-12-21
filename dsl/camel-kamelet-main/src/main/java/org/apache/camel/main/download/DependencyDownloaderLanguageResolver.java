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
import org.apache.camel.main.stub.StubLanguage;
import org.apache.camel.main.util.SuggestSimilarHelper;
import org.apache.camel.spi.Language;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * Auto downloaded needed JARs when resolving languages.
 */
public final class DependencyDownloaderLanguageResolver extends DefaultLanguageResolver {

    private static final String ACCEPTED_STUB_NAMES
            = "constant,exchangeProperty,header,ref,simple";

    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private final DependencyDownloader downloader;
    private final String stubPattern;
    private final boolean silent;

    public DependencyDownloaderLanguageResolver(CamelContext camelContext, String stubPattern, boolean silent) {
        this.downloader = camelContext.hasService(DependencyDownloader.class);
        this.stubPattern = stubPattern;
        this.silent = silent;
    }

    @Override
    public Language resolveLanguage(String name, CamelContext context) {
        LanguageModel model = catalog.languageModel(name);
        if (model != null) {
            downloadLoader(model.getGroupId(), model.getArtifactId(), model.getVersion());
            if ("csimple".equals(name)) {
                // need to include joor compiler also
                downloadLoader(model.getGroupId(), "camel-csimple-joor", model.getVersion());
            }
        }

        Language answer;
        boolean accept = accept(name);
        if (accept) {
            answer = super.resolveLanguage(name, context);
        } else {
            answer = new StubLanguage();
        }

        if (answer == null) {
            List<String> suggestion = SuggestSimilarHelper.didYouMean(catalog.findDataFormatNames(), name);
            if (suggestion != null && !suggestion.isEmpty()) {
                String s = String.join(", ", suggestion);
                throw new IllegalArgumentException("Cannot find language with name: " + name + ". Did you mean: " + s);
            }
        }
        return answer;
    }

    private void downloadLoader(String groupId, String artifactId, String version) {
        if (!downloader.alreadyOnClasspath(groupId, artifactId, version)) {
            downloader.downloadDependency(groupId, artifactId, version);
        }
    }

    private boolean accept(String name) {
        if (stubPattern == null) {
            return true;
        }

        // we are stubbing but need to accept the following
        return ACCEPTED_STUB_NAMES.contains(name);
    }

}
