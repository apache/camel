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

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoConfigureDownloadListener implements DownloadListener, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(AutoConfigureDownloadListener.class);

    private CamelContext camelContext;
    private final Set<String> artifacts = new HashSet<>();

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onDownloadDependency(String groupId, String artifactId, String version) {
        // noop
    }

    @Override
    public void onDownloadedDependency(String groupId, String artifactId, String version) {
        if (!artifacts.contains(artifactId)) {
            artifacts.add(artifactId);
            autoConfigureDependencies(artifactId);
            autoConfigure(artifactId);
        }
    }

    @Override
    public void onAlreadyDownloadedDependency(String groupId, String artifactId, String version) {
        // noop
    }

    protected void autoConfigureDependencies(String artifactId) {
        // the auto-configuration may require additional dependencies being downloaded first
        InputStream is = getClass().getResourceAsStream("/auto-configure/" + artifactId + ".properties");
        if (is != null) {
            try {
                String script = IOHelper.loadText(is);
                for (String line : script.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("dependency=")) {
                        MavenGav gav = MavenGav.parseGav(line.substring(11));
                        DependencyDownloader downloader = getCamelContext().hasService(DependencyDownloader.class);
                        // these are extra dependencies used in special use-case so download as hidden
                        downloader.downloadHiddenDependency(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
                    }
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            } finally {
                IOHelper.close(is);
            }
        }
    }

    protected void autoConfigure(String artifactId) {
        // is there any special auto configuration scripts?
        InputStream is = getClass().getResourceAsStream("/auto-configure/" + artifactId + ".joor");
        if (is != null) {
            try {
                // ensure java-joor is downloaded
                DependencyDownloader downloader = getCamelContext().hasService(DependencyDownloader.class);
                // these are extra dependencies used in special use-case so download as hidden
                downloader.downloadHiddenDependency("org.apache.camel", "camel-joor", camelContext.getVersion());
                // execute script via java-joor
                String script = IOHelper.loadText(is);
                Language lan = camelContext.resolveLanguage("joor");
                Expression exp = lan.createExpression(script);
                Object out = exp.evaluate(new DefaultExchange(camelContext), Object.class);
                if (ObjectHelper.isNotEmpty(out)) {
                    LOG.info("{}", out);
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            } finally {
                IOHelper.close(is);
            }
        }
    }

}
