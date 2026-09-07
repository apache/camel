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

import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesReload;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.SecretRotationAware;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ContextReloadStrategy}.
 */
@ManagedResource(description = "Managed DefaultContextReloadStrategy")
public class DefaultContextReloadStrategy extends ServiceSupport implements ContextReloadStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultContextReloadStrategy.class);

    private CamelContext camelContext;
    private int succeeded;
    private int failed;
    private Exception lastError;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @ManagedOperation(description = "Trigger on-demand reloading")
    public void onReload() {
        onReload("JMX Management");
    }

    @Override
    public void onReload(Object source) {
        LOG.info("Reloading CamelContext ({}) triggered by: {}", camelContext.getName(), source);
        try {
            lastError = null;
            EventHelper.notifyContextReloading(getCamelContext(), source);
            reloadProperties(source);
            // the order matters: the components must hold the newly resolved secrets before they are asked
            // to re-authenticate, and both must happen before the routes come back up, so that the new
            // consumers and producers are created against an already re-authenticated resource
            reloadComponentProperties(source);
            notifySecretRotation(source);
            reloadRoutes(source);
            incSucceededCounter();
            EventHelper.notifyContextReloaded(getCamelContext(), source);
        } catch (Exception e) {
            lastError = e;
            incFailedCounter();
            LOG.warn("Error reloading CamelContext ({}) due to: {}", camelContext.getName(), e.getMessage(), e);
            EventHelper.notifyContextReloadFailure(getCamelContext(), source, e);
        }
    }

    protected void reloadRoutes(Object source) throws Exception {
        getCamelContext().getRouteController().reloadAllRoutes();
    }

    protected void reloadProperties(Object source) throws Exception {
        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        for (PropertiesSource ps : pc.getPropertiesSources()) {
            // reload by restarting
            ServiceHelper.stopAndShutdownService(ps);
            ServiceHelper.startService(ps);
        }
    }

    /**
     * Re-applies the configuration properties whose value is a property placeholder, so that components are
     * re-configured with what those placeholders resolve to now.
     * <p/>
     * A component option such as <tt>camel.component.kafka.saslJaasConfig</tt> has its placeholder resolved once, when
     * the component is configured, and the resolved value is what is stored on the component. Reloading the routes
     * rebuilds the endpoints from that same already-resolved value, so without this step a rotated secret would never
     * reach the component. Only <tt>camel.</tt> options whose value is a placeholder are re-applied, as they are the
     * only ones whose resolved value can change while the raw configuration stays the same.
     */
    protected void reloadComponentProperties(Object source) throws Exception {
        PropertiesReload pr = getCamelContext().hasService(PropertiesReload.class);
        if (pr == null) {
            // component re-configuration is only supported when running with Camel Main
            return;
        }

        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        Properties prop = pc.loadProperties();
        // stringPropertyNames is a live view of the keys, so snapshot before removing
        Set<String> keys = new LinkedHashSet<>(prop.stringPropertyNames());
        for (String key : keys) {
            Object value = prop.get(key);
            boolean placeholder = key.startsWith("camel.")
                    && value instanceof String str && str.contains(PropertiesComponent.PREFIX_TOKEN);
            if (!placeholder) {
                prop.remove(key);
            }
        }
        if (!prop.isEmpty()) {
            LOG.debug("Re-applying {} property placeholder based options to components", prop.size());
            pr.onReload(source != null ? source.toString() : "ContextReload", prop);
        }
    }

    /**
     * Notifies every {@link SecretRotationAware} component and registry bean that the secrets they captured may have
     * been rotated, so they can re-authenticate before the routes are restarted.
     * <p/>
     * A listener that throws is logged and skipped, so that one component cannot prevent the others from being
     * refreshed, nor fail the reload as a whole.
     */
    protected void notifySecretRotation(Object source) {
        Set<SecretRotationAware> targets = new LinkedHashSet<>();
        for (String name : getCamelContext().getComponentNames()) {
            Component component = getCamelContext().hasComponent(name);
            if (component instanceof SecretRotationAware sra) {
                targets.add(sra);
            }
        }
        targets.addAll(getCamelContext().getRegistry().findByType(SecretRotationAware.class));

        for (SecretRotationAware target : targets) {
            try {
                target.onSecretRotation(source);
            } catch (Exception e) {
                LOG.warn("Error re-authenticating {} after secret rotation due to: {}. This exception is ignored.",
                        target, e.getMessage(), e);
            }
        }
    }

    @ManagedAttribute(description = "Number of reloads succeeded")
    public int getReloadCounter() {
        return succeeded;
    }

    @ManagedAttribute(description = "Number of reloads failed")
    public int getFailedCounter() {
        return failed;
    }

    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    @ManagedOperation(description = "Reset counters")
    public void resetCounters() {
        succeeded = 0;
        failed = 0;
    }

    @Override
    public Exception getLastError() {
        return lastError;
    }

    protected void incSucceededCounter() {
        succeeded++;
    }

    protected void incFailedCounter() {
        failed++;
    }

}
