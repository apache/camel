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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for triggering on-demand loading of Camel routes in a running Camel application. The strategy is triggered
 * on-demand and reload all files provided from external source such as camel-jbang
 */
@ManagedResource(description = "Managed LoadOnDemandReloadStrategy")
public class LoadOnDemandReloadStrategy extends RouteOnDemandReloadStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(LoadOnDemandReloadStrategy.class);

    public LoadOnDemandReloadStrategy() {
        setRemoveAllRoutes(false);
        setScheduler(false);
    }

    /**
     * Triggers on-demand loading
     *
     * @param source  the source calling this
     * @param files   a list of file names
     * @param restart whether to force restart all routes after source files are loaded
     */
    public void load(Object source, List<String> files, boolean restart) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            setLastError(null);
            // use bootstrap classloader from camel so its consistent
            ClassLoader acl = getCamelContext().getApplicationContextClassLoader();
            if (acl != null) {
                Thread.currentThread().setContextClassLoader(acl);
            }
            doOnReload(source, files, restart);
            incSucceededCounter();
        } catch (Exception e) {
            setLastError(e);
            incFailedCounter();
            LOG.warn("Error loading routes due to {}. This exception is ignored.", e.getMessage(), e);
        } finally {
            if (cl != null) {
                Thread.currentThread().setContextClassLoader(cl);
            }
        }
    }

    protected void doOnReload(Object source, List<String> files, boolean restart) throws Exception {
        List<Resource> properties = new ArrayList<>();
        List<Resource> groovy = new ArrayList<>();
        List<Resource> routes = new ArrayList<>();

        for (Resource res : findReloadedResources(files)) {
            String ext = FileUtil.onlyExt(res.getLocation());
            if ("properties".equals(ext)) {
                properties.add(res);
            } else if ("groovy".equals(ext)) {
                groovy.add(res);
            } else {
                routes.add(res);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "On-demand reload scanned {} files (properties: {}, routes: {}, groovy: {})",
                    properties.size() + routes.size(),
                    properties.size(),
                    routes.size(),
                    groovy.size());
        }

        // reload properties first
        boolean reloaded = false;
        for (Resource res : properties) {
            reloaded |= onPropertiesReload(res, false);
        }
        for (Resource res : groovy) {
            reloaded |= onGroovyReload(res, false);
        }
        if (!routes.isEmpty()) {
            onRouteReload(routes, false);
        }
        if (restart || reloaded) {
            ContextReloadStrategy crs = getCamelContext().hasService(ContextReloadStrategy.class);
            if (crs != null) {
                crs.onReload(source);
            }
        }
    }

    @Override
    public void onReload(Object source) {
        // noop
    }

    @Override
    protected List<Resource> findReloadedResources(Object source) throws Exception {
        List<Resource> answer = new ArrayList<>();

        if (source instanceof List list) {
            for (Object l : list) {
                File f = new File(l.toString());
                if (f.isFile() && f.exists()) {
                    Resource res = ResourceHelper.resolveResource(getCamelContext(), "file:" + f.getAbsolutePath());
                    answer.add(res);
                }
            }
        }

        return answer;
    }
}
