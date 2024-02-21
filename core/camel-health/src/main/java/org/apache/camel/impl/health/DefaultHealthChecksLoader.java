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
package org.apache.camel.impl.health;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To load custom {@link HealthCheck} by classpath scanning.
 */
public class DefaultHealthChecksLoader {

    public static final String META_INF_SERVICES = "META-INF/services/org/apache/camel/health-check";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHealthChecksLoader.class);

    protected final CamelContext camelContext;
    protected final PackageScanResourceResolver resolver;
    protected final HealthCheckResolver healthCheckResolver;

    public DefaultHealthChecksLoader(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.resolver = PluginHelper.getPackageScanResourceResolver(camelContext);
        this.healthCheckResolver = PluginHelper.getHealthCheckResolver(camelContext);
    }

    public Collection<HealthCheck> loadHealthChecks() {
        Collection<HealthCheck> answer = new ArrayList<>();

        LOG.trace("Searching for {} health checks", META_INF_SERVICES);

        try {
            Collection<Resource> resources = resolver.findResources(META_INF_SERVICES + "/*-check");
            if (LOG.isDebugEnabled()) {
                LOG.debug("Discovered {} health checks from classpath scanning", resources.size());
            }
            for (Resource resource : resources) {
                LOG.trace("Resource: {}", resource);
                if (acceptResource(resource)) {
                    String id = extractId(resource);
                    LOG.trace("Loading HealthCheck: {}", id);
                    HealthCheck hc = healthCheckResolver.resolveHealthCheck(id);
                    if (hc != null) {
                        LOG.debug("Loaded HealthCheck: {}/{}", hc.getGroup(), hc.getId());
                        answer.add(hc);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error during scanning for custom health-checks on classpath due to: {}. This exception is ignored.",
                    e.getMessage());
        }

        return answer;
    }

    protected boolean acceptResource(Resource resource) {
        String loc = resource.getLocation();
        if (loc == null) {
            return false;
        }

        // this is an out of the box health-check
        if (loc.endsWith("context-check")) {
            return false;
        }

        return true;
    }

    protected String extractId(Resource resource) {
        String loc = resource.getLocation();
        loc = StringHelper.after(loc, META_INF_SERVICES + "/");
        // remove -check suffix
        if (loc != null && loc.endsWith("-check")) {
            loc = loc.substring(0, loc.length() - 6);
        }
        return loc;
    }

}
