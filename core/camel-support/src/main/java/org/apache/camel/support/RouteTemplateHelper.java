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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.RouteTemplateLoaderListener;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for working with route templates.
 */
public final class RouteTemplateHelper {

    private static final Logger LOG = LoggerFactory.getLogger(RouteTemplateHelper.class);

    private RouteTemplateHelper() {
    }

    /**
     * Loads the route template with the given template id from a given location. After the template is loaded, it is
     * automatic added to the {@link CamelContext}.
     *
     * @param  camelContext the camel context
     * @param  listener     optional listener for when a route template is being loaded
     * @param  templateId   the template id
     * @param  location     location of the route template to load as a resource such as from classpath or file system
     * @throws Exception    is thrown if any kind of error loading the route template
     */
    public static void loadRouteTemplateFromLocation(
            CamelContext camelContext, RouteTemplateLoaderListener listener,
            String templateId, String location)
            throws Exception {
        if (location == null) {
            throw new IllegalArgumentException("Location is empty");
        }

        boolean found = false;
        final ResourceLoader resourceLoader = PluginHelper.getResourceLoader(camelContext);
        for (String path : location.split(",")) {
            // using dot as current dir must be expanded into absolute path
            if (".".equals(path) || "file:.".equals(path)) {
                path = new File(".").getAbsolutePath();
                path = "file:" + FileUtil.onlyPath(path);
            }
            String name = path;
            Resource res = null;
            // first try resource as-is if the path has an extension
            String ext = FileUtil.onlyExt(path);
            if (ext != null && !ext.isEmpty()) {
                res = resourceLoader.resolveResource(name);
            }
            if (res == null || !res.exists()) {
                if (!path.endsWith("/")) {
                    path += "/";
                }
                name = path + templateId + ".kamelet.yaml";
                res = resourceLoader.resolveResource(name);
            }
            if (res.exists()) {
                try {
                    if (listener != null) {
                        listener.loadRouteTemplate(res);
                    }
                } catch (Exception e) {
                    LOG.warn("RouteTemplateLoaderListener error due to {}. This exception is ignored", e.getMessage(), e);
                }
                PluginHelper.getRoutesLoader(camelContext).loadRoutes(res);
                found = true;
                break;
            }
        }
        if (!found) {
            // fallback to old behaviour
            String path = location;
            // using dot as current dir must be expanded into absolute path
            if (".".equals(path) || "file:.".equals(path)) {
                path = new File(".").getAbsolutePath();
                path = "file:" + FileUtil.onlyPath(path);
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            String target = path + templateId + ".kamelet.yaml";
            PluginHelper.getRoutesLoader(camelContext).loadRoutes(
                    resourceLoader.resolveResource(target));
        }
    }
}
