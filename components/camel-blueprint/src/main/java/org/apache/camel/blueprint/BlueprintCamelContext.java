/**
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
package org.apache.camel.blueprint;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.TypeConverter;
import org.apache.camel.blueprint.handler.CamelNamespaceHandler;
import org.apache.camel.core.osgi.OsgiCamelContextHelper;
import org.apache.camel.core.osgi.OsgiCamelContextPublisher;
import org.apache.camel.core.osgi.OsgiFactoryFinderResolver;
import org.apache.camel.core.osgi.OsgiTypeConverter;
import org.apache.camel.core.osgi.utils.BundleContextUtils;
import org.apache.camel.core.osgi.utils.BundleDelegatingClassLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.LoadPropertiesException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.BlueprintEvent;
import org.osgi.service.blueprint.container.BlueprintListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OSGi Blueprint based {@link org.apache.camel.CamelContext}.
 */
public class BlueprintCamelContext extends DefaultCamelContext implements ServiceListener, BlueprintListener {

    private static final Logger LOG = LoggerFactory.getLogger(BlueprintCamelContext.class);
    
    private BundleContext bundleContext;
    private BlueprintContainer blueprintContainer;
    private ServiceRegistration<?> registration;

    public BlueprintCamelContext() {
    }

    public BlueprintCamelContext(BundleContext bundleContext, BlueprintContainer blueprintContainer) {
        this.bundleContext = bundleContext;
        this.blueprintContainer = blueprintContainer;

        // inject common osgi
        OsgiCamelContextHelper.osgiUpdate(this, bundleContext);

        // and these are blueprint specific
        setComponentResolver(new BlueprintComponentResolver(bundleContext));
        setLanguageResolver(new BlueprintLanguageResolver(bundleContext));
        setDataFormatResolver(new BlueprintDataFormatResolver(bundleContext));
        setApplicationContextClassLoader(new BundleDelegatingClassLoader(bundleContext.getBundle()));
        // must use classloader of the namespace handler
        setModelJAXBContextFactory(new BlueprintModelJAXBContextFactory(CamelNamespaceHandler.class.getClassLoader()));
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public BlueprintContainer getBlueprintContainer() {
        return blueprintContainer;
    }

    public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }
   
    public void init() throws Exception {
        LOG.trace("init {}", this);

        // add service listener so we can be notified when blueprint container is done
        // and we would be ready to start CamelContext
        bundleContext.addServiceListener(this);
        // add blueprint listener as service, as we need this for the blueprint container
        // to support change events when it changes states
        registration = bundleContext.registerService(BlueprintListener.class, this, null);
    }

    public void destroy() throws Exception {
        LOG.trace("destroy {}", this);

        // remove listener and stop this CamelContext
        try {
            bundleContext.removeServiceListener(this);
        } catch (Exception e) {
            LOG.warn("Error removing ServiceListener " + this + ". This exception is ignored.", e);
        }
        if (registration != null) {
            try {
                registration.unregister();
            } catch (Exception e) {
                LOG.warn("Error unregistering service registration " + registration + ". This exception is ignored.", e);
            }
            registration = null;
        }

        // must stop Camel
        stop();
    }

    @Override
    public Map<String, Properties> findComponents() throws LoadPropertiesException, IOException {
        return BundleContextUtils.findComponents(bundleContext, this);
    }

    @Override
    public String getComponentDocumentation(String componentName) throws IOException {
        return BundleContextUtils.getComponentDocumentation(bundleContext, this, componentName);
    }

    @Override
    public void blueprintEvent(BlueprintEvent event) {
        // noop as we just needed to enlist the BlueprintListener to have events triggered to serviceChanged method
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Service {} changed to {}", event, event.getType());
        }
        // look for blueprint container to be registered, and then we can start the CamelContext
        if (event.getType() == ServiceEvent.REGISTERED
                && event.getServiceReference().isAssignableTo(bundleContext.getBundle(), "org.osgi.service.blueprint.container.BlueprintContainer")
                && bundleContext.getBundle().equals(event.getServiceReference().getBundle())) {
            try {
                maybeStart();
            } catch (Exception e) {
                LOG.error("Error occurred during starting Camel: " + this + " due " + e.getMessage(), e);
            }
        }
    }

    @Override
    protected TypeConverter createTypeConverter() {
        // CAMEL-3614: make sure we use a bundle context which imports org.apache.camel.impl.converter package
        BundleContext ctx = BundleContextUtils.getBundleContext(getClass());
        if (ctx == null) {
            ctx = bundleContext;
        }
        FactoryFinder finder = new OsgiFactoryFinderResolver(bundleContext).resolveDefaultFactoryFinder(getClassResolver());
        return new OsgiTypeConverter(ctx, getInjector(), finder);
    }

    @Override
    protected Registry createRegistry() {
        Registry reg = new BlueprintContainerRegistry(getBlueprintContainer());
        return OsgiCamelContextHelper.wrapRegistry(this, reg, bundleContext);
    }
    
    @Override
    public void start() throws Exception {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            // let's set a more suitable TCCL while starting the context
            Thread.currentThread().setContextClassLoader(getApplicationContextClassLoader());
            super.start();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private void maybeStart() throws Exception {
        LOG.trace("maybeStart: {}", this);

        // allow to register the BluerintCamelContext eager in the OSGi Service Registry, which ex is needed
        // for unit testing with camel-test-blueprint
        boolean eager = "true".equalsIgnoreCase(System.getProperty("registerBlueprintCamelContextEager"));
        if (eager) {
            for (EventNotifier notifier : getManagementStrategy().getEventNotifiers()) {
                if (notifier instanceof OsgiCamelContextPublisher) {
                    OsgiCamelContextPublisher publisher = (OsgiCamelContextPublisher) notifier;
                    publisher.registerCamelContext(this);
                    break;
                }
            }
        }

        // for example from unit testing we want to start Camel later and not
        // when blueprint loading the bundle
        boolean skip = "true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"));
        if (skip) {
            LOG.trace("maybeStart: {} is skipping as System property skipStartingCamelContext is set", this);
            return;
        }

        if (!isStarted() && !isStarting()) {
            LOG.debug("Starting {}", this);
            start();
        } else {
            // ignore as Camel is already started
            LOG.trace("Ignoring maybeStart() as {} is already started", this);
        }
    }

}
