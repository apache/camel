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
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DependencyDownloaderPropertiesComponent extends ServiceSupport implements StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyDownloaderPropertiesComponent.class);

    private final CamelContext camelContext;
    private final KnownDependenciesResolver knownDependenciesResolver;
    private final DependencyDownloader downloader;
    private final boolean silent;
    private Properties properties;

    public DependencyDownloaderPropertiesComponent(
            CamelContext camelContext, KnownDependenciesResolver knownDependenciesResolver, boolean silent) {
        this.camelContext = camelContext;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
        this.knownDependenciesResolver = knownDependenciesResolver;
        this.silent = silent;
    }

    @Override
    protected void doBuild() throws Exception {
        camelContext.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                // scan properties and detect dependencies to download and services to auto-configure
                properties = camelContext.getPropertiesComponent().loadProperties();
                resolveKnownDependencies();
                autoConfigureServices();
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.CamelContextInitializingEvent;
            }
        });
    }

    protected void autoConfigureServices() {
        for (String key : properties.stringPropertyNames()) {
            autoConfigure(key);
        }
    }

    protected void resolveKnownDependencies() {
        for (String key : properties.stringPropertyNames()) {
            // check both key and values (and combined)
            String value = properties.getProperty(key);
            MavenGav gav = knownDependenciesResolver.mavenGavForClass(key);
            if (gav != null) {
                downloadLoader(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
            }
            gav = knownDependenciesResolver.mavenGavForClass(value);
            if (gav != null) {
                downloadLoader(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
            }
            String line = key + "=" + value;
            gav = knownDependenciesResolver.mavenGavForClass(line);
            if (gav != null) {
                downloadLoader(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
            }
        }
    }

    private void downloadLoader(String groupId, String artifactId, String version) {
        if (!downloader.alreadyOnClasspath(groupId, artifactId, version)) {
            downloader.downloadDependency(groupId, artifactId, version);
        }
    }

    protected void autoConfigure(String key) {
        var config = new org.apache.camel.util.OrderedLocationProperties();
        config.putAll("camel-main", camelContext.getPropertiesComponent().loadProperties());

        // is there any special auto configuration scripts?
        InputStream is = getClass().getResourceAsStream("/auto-configure/" + key + ".java");
        if (is != null) {
            try {
                // ensure java-joor is downloaded
                DependencyDownloader downloader = camelContext.hasService(DependencyDownloader.class);
                // these are extra dependencies used in special use-case so download as hidden
                downloader.downloadHiddenDependency("org.apache.camel", "camel-joor", camelContext.getVersion());
                // execute script via java-joor
                String script = IOHelper.loadText(is);
                Language lan = camelContext.resolveLanguage("java");
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
