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

import org.apache.camel.TypeConverter;
import org.apache.camel.core.osgi.OsgiCamelContextHelper;
import org.apache.camel.core.osgi.OsgiCamelContextNameStrategy;
import org.apache.camel.core.osgi.OsgiClassResolver;
import org.apache.camel.core.osgi.OsgiFactoryFinderResolver;
import org.apache.camel.core.osgi.OsgiPackageScanClassResolver;
import org.apache.camel.core.osgi.OsgiServiceRegistry;
import org.apache.camel.core.osgi.OsgiTypeConverter;
import org.apache.camel.impl.CompositeRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;

public class BlueprintCamelContext extends DefaultCamelContext {

    private static final transient Log LOG = LogFactory.getLog(BlueprintCamelContext.class);

    private BundleContext bundleContext;
    private BlueprintContainer blueprintContainer;

    public BlueprintCamelContext() {
    }

    public BlueprintCamelContext(BundleContext bundleContext, BlueprintContainer blueprintContainer) {
        this.bundleContext = bundleContext;
        this.blueprintContainer = blueprintContainer;
        setNameStrategy(new OsgiCamelContextNameStrategy(bundleContext));
        setClassResolver(new OsgiClassResolver(bundleContext));
        setFactoryFinderResolver(new OsgiFactoryFinderResolver(bundleContext));
        setPackageScanClassResolver(new OsgiPackageScanClassResolver(bundleContext));
        setComponentResolver(new BlueprintComponentResolver(bundleContext));
        setLanguageResolver(new BlueprintLanguageResolver(bundleContext));
        setDataFormatResolver(new BlueprintDataFormatResolver(bundleContext));
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
        maybeStart();
    }

    private void maybeStart() throws Exception {
        if (!isStarted() && !isStarting()) {
            // Make sure we will not get into the endless loop of calling star
            LOG.info("Starting Apache Camel as property ShouldStartContext is true");
            start();
        } else {
            // ignore as Camel is already started
            LOG.trace("Ignoring maybeStart() as Apache Camel is already started");
        }
    }

    public void destroy() throws Exception {
        stop();
    }

    @Override
    protected TypeConverter createTypeConverter() {
        return new OsgiTypeConverter(bundleContext, getInjector());
    }

    @Override
    protected Registry createRegistry() {
        Registry reg = new BlueprintContainerRegistry(getBlueprintContainer());
        return OsgiCamelContextHelper.wrapRegistry(this, reg, bundleContext);
    }

}
