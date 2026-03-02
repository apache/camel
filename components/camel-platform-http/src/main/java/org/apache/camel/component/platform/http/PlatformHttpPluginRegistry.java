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
package org.apache.camel.component.platform.http;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.platform.http.spi.PlatformHttpPlugin;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link org.apache.camel.component.platform.http.spi.PlatformHttpPluginRegistry}.
 */
@JdkService(PlatformHttpPluginRegistry.FACTORY)
public class PlatformHttpPluginRegistry extends ServiceSupport
        implements org.apache.camel.component.platform.http.spi.PlatformHttpPluginRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformHttpPluginRegistry.class);

    private CamelContext camelContext;
    private final Set<PlatformHttpPlugin> plugins = new TreeSet<>(Comparator.comparing(PlatformHttpPlugin::getId));

    @Override
    public <T extends PlatformHttpPlugin> Optional<T> resolvePluginById(String id, Class<T> type) {
        PlatformHttpPlugin answer = plugins.stream().filter(plugin -> plugin.getId().equals(id)).findFirst()
                .orElse(getCamelContext().getRegistry().findByTypeWithName(PlatformHttpPlugin.class).get(id));
        if (answer == null) {
            answer = resolvePluginWithFactoryFinderById(id);

        }
        if (answer != null) {
            register(answer);
        }
        return Optional.ofNullable(type.cast(answer));
    }

    @Override
    public boolean register(PlatformHttpPlugin plugin) {
        if (getPlugin(plugin.getId()).isPresent()) {
            return false;
        }

        boolean result = plugins.add(plugin);
        if (result) {
            CamelContextAware.trySetCamelContext(plugin, camelContext);
            ServiceHelper.startService(plugin);
            LOG.debug("platform-http-plugin with id {} successfully registered", plugin.getId());
        }
        return result;
    }

    private Optional<PlatformHttpPlugin> getPlugin(String id) {
        return plugins.stream()
                .filter(r -> ObjectHelper.equal(r.getId(), id))
                .findFirst();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    private PlatformHttpPlugin resolvePluginWithFactoryFinderById(String id) {
        return ResolverHelper.resolveBootstrapService(camelContext, "platform-http/" + id, PlatformHttpPlugin.class)
                .orElse(null);
    }

}
