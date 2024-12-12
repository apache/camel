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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.spi.CompilePreProcessor;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.PojoBeanModel;

/**
 * Java DSL that can detect known classes from the java imports and download JARs
 */
public class JavaKnownImportsDownloader implements CompilePreProcessor {

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^import\\s+([a-zA-Z][.\\w]*)\\s*;", Pattern.MULTILINE);

    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private final DependencyDownloader downloader;
    private final KnownDependenciesResolver knownDependenciesResolver;

    public JavaKnownImportsDownloader(CamelContext camelContext, KnownDependenciesResolver knownDependenciesResolver) {
        this.downloader = camelContext.hasService(DependencyDownloader.class);
        this.knownDependenciesResolver = knownDependenciesResolver;
        camelContext.getRegistry().bind("JavaJoorKnownImportsDownloader", this);
    }

    @Override
    public void preCompile(CamelContext camelContext, String name, String code) throws Exception {
        List<String> imports = determineImports(code);
        for (String imp : imports) {
            // attempt known dependency resolver first
            MavenGav gav = knownDependenciesResolver.mavenGavForClass(imp);
            if (gav != null) {
                downloadLoader(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
            } else {
                // is this a known bean then we can determine the dependency
                for (String n : catalog.findBeansNames()) {
                    PojoBeanModel m = catalog.pojoBeanModel(n);
                    if (m != null && imp.equals(m.getJavaType())) {
                        downloadLoader(m.getGroupId(), m.getArtifactId(), m.getVersion());
                        break;
                    }
                }
            }
        }
    }

    private void downloadLoader(String groupId, String artifactId, String version) {
        if (!downloader.alreadyOnClasspath(groupId, artifactId, version)) {
            downloader.downloadDependency(groupId, artifactId, version);
        }
    }

    private static List<String> determineImports(String content) {
        List<String> answer = new ArrayList<>();
        final Matcher matcher = IMPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            String imp = matcher.group(1);
            imp = imp.trim();
            answer.add(imp);
        }
        return answer;
    }

}
