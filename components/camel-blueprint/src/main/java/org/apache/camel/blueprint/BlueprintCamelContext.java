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
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.apache.camel.spi.ModelJAXBContextFactory;
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
    
    protected final AtomicBoolean routeDefinitionValid = new AtomicBoolean(true);

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
    }

    @Override
    protected ModelJAXBContextFactory createModelJAXBContextFactory() {
        // must use classloader of the namespace handler
        return new BlueprintModelJAXBContextFactory(CamelNamespaceHandler.class.getClassLoader());
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
    public void blueprintEvent(BlueprintEvent event) {
        if (LOG.isDebugEnabled()) {
            String eventTypeString;

            switch (event.getType()) {
            case BlueprintEvent.CREATING:
                eventTypeString = "CREATING";
                break;
            case BlueprintEvent.CREATED:
                eventTypeString = "CREATED";
                break;
            case BlueprintEvent.DESTROYING:
                eventTypeString = "DESTROYING";
                break;
            case BlueprintEvent.DESTROYED:
                eventTypeString = "DESTROYED";
                break;
            case BlueprintEvent.GRACE_PERIOD:
                eventTypeString = "GRACE_PERIOD";
                break;
            case BlueprintEvent.WAITING:
                eventTypeString = "WAITING";
                break;
            case BlueprintEvent.FAILURE:
                eventTypeString = "FAILURE";
                break;
            default:
                eventTypeString = "UNKNOWN";
                break;
            }

            LOG.debug("Received BlueprintEvent[ replay={} type={} bundle={}] %s", event.isReplay(), eventTypeString, event.getBundle().getSymbolicName(), event.toString());
        }

        if (!event.isReplay() && this.getBundleContext().getBundle().getBundleId() == event.getBundle().getBundleId()) {
            if (event.getType() == BlueprintEvent.CREATED) {
                try {
                    LOG.info("Attempting to start Camel Context {}", this.getName());
                    this.maybeStart();
                } catch (Exception startEx) {
                    LOG.error("Error occurred during starting Camel Context  " + this.getName(), startEx);
                }
            } else if (event.getType() == BlueprintEvent.DESTROYING) {
                try {
                    LOG.info("Stopping Camel Context {}", this.getName());
                    this.stop();
                } catch (Exception stopEx) {
                    LOG.error("Error occurred during stopping Camel Context " + this.getName(), stopEx);
                }

            }
        }

    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        if (LOG.isDebugEnabled()) {
            String eventTypeString;

            switch (event.getType()) {
            case ServiceEvent.REGISTERED:
                eventTypeString = "REGISTERED";
                break;
            case ServiceEvent.MODIFIED:
                eventTypeString = "MODIFIED";
                break;
            case ServiceEvent.UNREGISTERING:
                eventTypeString = "UNREGISTERING";
                break;
            case ServiceEvent.MODIFIED_ENDMATCH:
                eventTypeString = "MODIFIED_ENDMATCH";
                break;
            default:
                eventTypeString = "UNKNOWN";
                break;
            }

            LOG.debug("Service {} changed to {}", event.toString(), eventTypeString);
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
        return new OsgiTypeConverter(ctx, this, getInjector(), finder);
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
        } catch (Exception e) {
            routeDefinitionValid.set(false);
            throw e;
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private void maybeStart() throws Exception {
        LOG.trace("maybeStart: {}", this);

        if (!routeDefinitionValid.get()) {
            LOG.trace("maybeStart: {} is skipping since CamelRoute definition is not correct.", this);
            return;
        }

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
