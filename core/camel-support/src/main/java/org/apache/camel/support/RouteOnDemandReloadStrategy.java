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
package org.apache.camel.support;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for triggering on-demand reloading of Camel routes in a running Camel application. The strategy is triggered
 * on-demand and reload all files from a directory (and subdirectories).
 */
@ManagedResource(description = "Managed RouteOnDemandReloadStrategy")
public class RouteOnDemandReloadStrategy extends RouteWatcherReloadStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(RouteOnDemandReloadStrategy.class);

    public RouteOnDemandReloadStrategy() {
        setScheduler(false);
    }

    public RouteOnDemandReloadStrategy(String directory) {
        super(directory);
        setScheduler(false);
    }

    public RouteOnDemandReloadStrategy(String directory, boolean recursive) {
        super(directory, recursive);
        setScheduler(false);
    }

    /**
     * Triggers on-demand reloading
     */
    @ManagedOperation(description = "Trigger on-demand reloading")
    public void onReload() {
        onReload("JMX Management");
    }

    /**
     * Triggers on-demand reloading
     */
    @Override
    public void onReload(Object source) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            // use bootstrap classloader from camel so its consistent
            ClassLoader acl = getCamelContext().getApplicationContextClassLoader();
            if (acl != null) {
                Thread.currentThread().setContextClassLoader(acl);
            }
            doOnReload(source);
            incSucceededCounter();
        } catch (Exception e) {
            incFailedCounter();
            LOG.warn("Error reloading routes due to {}. This exception is ignored.", e.getMessage(), e);
        } finally {
            if (cl != null) {
                Thread.currentThread().setContextClassLoader(cl);
            }
        }
    }

    protected void doOnReload(Object source) throws Exception {
        List<Resource> properties = new ArrayList<>();
        List<Resource> routes = new ArrayList<>();

        File dir = new File(getFolder());
        for (Path path : ResourceHelper.findInFileSystem(dir.toPath(), getPattern())) {
            Resource res = ResourceHelper.resolveResource(getCamelContext(), "file:" + path.toString());
            String ext = FileUtil.onlyExt(path.getFileName().toString());
            if ("properties".equals(ext)) {
                properties.add(res);
            } else {
                routes.add(res);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("On-demand reload scanned {} files (properties: {}, routes: {})",
                    properties.size() + routes.size(), properties.size(), routes.size());
        }

        // reload properties first
        boolean reloaded = false;
        for (Resource res : properties) {
            reloaded |= onPropertiesReload(res, false);
        }
        boolean removeEverything = routes.isEmpty();
        if (reloaded || !routes.isEmpty()) {
            // trigger routes to also reload if properties was reloaded
            onRouteReload(routes, removeEverything);
        } else {
            // rare situation where all routes are deleted
            onRouteReload(null, removeEverything);
        }
    }

}
