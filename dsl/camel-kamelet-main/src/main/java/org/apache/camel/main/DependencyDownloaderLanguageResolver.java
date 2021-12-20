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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.engine.DefaultLanguageResolver;
import org.apache.camel.spi.Language;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * Auto downloaded needed JARs when resolving languages.
 */
final class DependencyDownloaderLanguageResolver extends DefaultLanguageResolver implements CamelContextAware {

    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private CamelContext camelContext;

    public DependencyDownloaderLanguageResolver(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Language resolveLanguage(String name, CamelContext context) {
        LanguageModel model = catalog.languageModel(name);
        if (model != null && !DownloaderHelper.alreadyOnClasspath(camelContext, model.getArtifactId())) {
            DownloaderHelper.downloadDependency(camelContext, model.getGroupId(), model.getArtifactId(), model.getVersion());
        }

        return super.resolveLanguage(name, context);
    }

}
