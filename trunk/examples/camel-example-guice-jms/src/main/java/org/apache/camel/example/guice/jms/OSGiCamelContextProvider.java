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
package org.apache.camel.example.guice.jms;

import java.util.Properties;
import java.util.Set;
import javax.naming.Context;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.osgi.CamelContextFactory;
import org.guiceyfruit.jndi.JndiBindings;
import org.guiceyfruit.jndi.internal.JndiContext;
import org.osgi.framework.BundleContext;

public class OSGiCamelContextProvider implements Provider<CamelContext> {
    private final CamelContextFactory factory;
    @Inject
    private Set<RoutesBuilder> routeBuilders;
    @Inject
    private Injector injector;
    
    public OSGiCamelContextProvider(BundleContext context) {
        // In this we can support to run this provider with or without OSGI
        if (context != null) {
            factory = new CamelContextFactory();
            factory.setBundleContext(context);
        } else {
            factory = null;
        }
    }
    
    protected Context getJndiContext() {
        try {
            JndiContext context = new JndiContext(null);
            if (injector != null) {
                //Feed the empty properties to get the code work
                JndiBindings.bindInjectorAndBindings(context, injector, new Properties());
            }
            return context;
        } catch (Exception e) {
            throw new ProvisionException("Failed to create JNDI bindings. Reason: " + e, e);
        }
    }
    
    // set the JndiRegistry to the camel context
    protected void updateRegistry(DefaultCamelContext camelContext) {
        camelContext.setRegistry(new JndiRegistry(getJndiContext()));
    }

    public CamelContext get() {
        DefaultCamelContext camelContext;
        if (factory != null) {
            camelContext = factory.createContext();
        } else {
            camelContext = new DefaultCamelContext();
        }
        if (routeBuilders != null) {
            for (RoutesBuilder builder : routeBuilders) {
                try {
                    camelContext.addRoutes(builder);
                } catch (Exception e) {
                    throw new ProvisionException("Failed to add the router. Reason: " + e, e);
                }
            }
        }
        updateRegistry(camelContext);        
        return camelContext;
    }

}
