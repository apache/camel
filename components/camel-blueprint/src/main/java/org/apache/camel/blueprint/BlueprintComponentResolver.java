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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.core.osgi.OsgiComponentResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.util.CamelContextHelper;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.NoSuchComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.getException;

public class BlueprintComponentResolver extends OsgiComponentResolver {

    private static final Logger LOG = LoggerFactory.getLogger(BlueprintComponentResolver.class);

    public BlueprintComponentResolver(BundleContext bundleContext) {
        super(bundleContext);
    }

    @Override
    public Component resolveComponent(String name, CamelContext context) throws Exception {
        try {
            Object bean = context.getRegistry().lookupByName(name);
            if (bean instanceof Component) {
                LOG.debug("Found component: {} in registry: {}", name, bean);
                return (Component) bean;
            } else {
                // let's use Camel's type conversion mechanism to convert things like CamelContext
                // and other types into a valid Component
                Component component = CamelContextHelper.convertTo(context, Component.class, bean);
                if (component != null) {
                    return component;
                }
            }
        } catch (Exception e) {
            if (getException(NoSuchComponentException.class, e) != null) {
                // if the caused error is NoSuchComponentException then that can be expected so ignore
            } else {
                LOG.trace("Ignored error looking up bean: " + name + " due: " + e.getMessage(), e);
            }
        }
        try {
            Object bean = context.getRegistry().lookupByName(".camelBlueprint.componentResolver." + name);
            if (bean instanceof ComponentResolver) {
                LOG.debug("Found component resolver: {} in registry: {}", name, bean);
                return ((ComponentResolver) bean).resolveComponent(name, context);
            }
        } catch (Exception e) {
            LOG.trace("Ignored error looking up bean: " + name + " due: " + e.getMessage(), e);
        }
        return getComponent(name, context);
    }

}
