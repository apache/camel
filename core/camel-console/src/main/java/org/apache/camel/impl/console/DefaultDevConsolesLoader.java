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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To load custom {@link org.apache.camel.console.DevConsole} by classpath scanning.
 */
public class DefaultDevConsolesLoader {

    public static final String META_INF_SERVICES = "META-INF/services/org/apache/camel/dev-console";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultDevConsolesLoader.class);

    protected final CamelContext camelContext;
    protected final PackageScanResourceResolver resolver;
    protected final DevConsoleResolver devConsoleResolver;

    public DefaultDevConsolesLoader(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.resolver = PluginHelper.getPackageScanResourceResolver(camelContext);
        this.devConsoleResolver = PluginHelper.getDevConsoleResolver(camelContext);
    }

    public Collection<DevConsole> loadDevConsoles() {
        return loadDevConsoles(false);
    }

    public Collection<DevConsole> loadDevConsoles(boolean force) {
        Collection<DevConsole> answer = new ArrayList<>();

        if (force) {
            // when forcing then restart resolver, so we can do a re-scan
            ServiceHelper.stopService(devConsoleResolver);
            ServiceHelper.startService(devConsoleResolver);
        }
        try {
            LOG.trace("Searching for {} dev consoles", META_INF_SERVICES);
            Collection<Resource> resources = resolver.findResources(META_INF_SERVICES + "/*");
            if (LOG.isDebugEnabled()) {
                LOG.debug("Discovered {} dev consoles from classpath scanning", resources.size());
            }
            for (Resource resource : resources) {
                LOG.trace("Resource: {}", resource);
                if (acceptResource(resource)) {
                    String id = extractId(resource);
                    LOG.trace("Loading DevConsole: {}", id);
                    DevConsole dc = devConsoleResolver.resolveDevConsole(id);
                    if (dc != null) {
                        LOG.debug("Loaded DevConsole: {}/{}", dc.getGroup(), dc.getId());
                        answer.add(dc);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error during scanning for custom dev-consoles on classpath due to: {}. This exception is ignored.",
                    e.getMessage());
        }

        return answer;
    }

    protected boolean acceptResource(Resource resource) {
        String loc = resource.getLocation();
        if (loc == null) {
            return false;
        }
        if (loc.endsWith("default-registry")) {
            // this is the registry so should be skipped
            return false;
        }

        return true;
    }

    protected String extractId(Resource resource) {
        String loc = resource.getLocation();
        return StringHelper.after(loc, META_INF_SERVICES + "/");
    }

}
