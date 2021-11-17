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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import groovy.grape.Grape;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auto downloaded needed JARs when resolving components.
 */
public class DependencyDownloaderComponentResolver extends DefaultComponentResolver implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(KameletDependencyDownloader.class);
    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private CamelContext camelContext;

    public DependencyDownloaderComponentResolver(CamelContext camelContext) {
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
    public Component resolveComponent(String name, CamelContext context) {
        ComponentModel model = catalog.componentModel(name);
        if (model != null && !alreadyOnClasspath(model.getArtifactId())) {
            downloadDependency(model.getGroupId(), model.getArtifactId(), model.getVersion());
        }

        return super.resolveComponent(name, context);
    }

    private boolean alreadyOnClasspath(String artifactId) {
        if (camelContext.getApplicationContextClassLoader() != null) {
            ClassLoader cl = camelContext.getApplicationContextClassLoader();
            if (cl instanceof URLClassLoader) {
                URLClassLoader ucl = (URLClassLoader) cl;
                for (URL u : ucl.getURLs()) {
                    String s = u.toString();
                    if (s.contains(artifactId)) {
                        // already on classpath
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void downloadDependency(String groupId, String artifactId, String version) {
        StopWatch watch = new StopWatch();
        Map<String, Object> map = new HashMap<>();
        map.put("classLoader", camelContext.getApplicationContextClassLoader());
        map.put("group", groupId);
        map.put("module", artifactId);
        map.put("version", version);
        map.put("classifier", "");

        LOG.debug("Downloading dependency: {}:{}:{}", groupId, artifactId, version);
        Grape.grab(map);
        LOG.info("Downloaded dependency: {}:{}:{} took: {}", groupId, artifactId, version,
                TimeUtils.printDuration(watch.taken()));
    }
}
