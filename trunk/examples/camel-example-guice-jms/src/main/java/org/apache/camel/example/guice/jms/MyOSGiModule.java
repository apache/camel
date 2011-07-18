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

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import com.google.inject.name.Names;
import org.apache.camel.CamelContext;
import org.osgi.framework.BundleContext;

/**
 * Configures the CamelContext, RouteBuilder, Component and Endpoint instances using
 * Guice within OSGi platform
 *
 * @version 
 */
public class MyOSGiModule extends MyModule {
    private OSGiCamelContextProvider provider;
    private Properties properties;
    
    MyOSGiModule(BundleContext bundleContext) throws IOException {
        super();
        provider = new OSGiCamelContextProvider(bundleContext);
        properties = new Properties();
        URL jndiPropertiesUrl;
        if (bundleContext != null) {
            jndiPropertiesUrl = bundleContext.getBundle().getEntry("camel.properties");
        } else {
            jndiPropertiesUrl = this.getClass().getResource("/camel.properties");
        }
        if (jndiPropertiesUrl != null) {
            properties.load(jndiPropertiesUrl.openStream());
        }
    }
    
    @Override
    protected void configureCamelContext() {
        bind(CamelContext.class).toProvider(provider).asEagerSingleton();
    }
    
    @Override
    protected void configure() {
        // loading the properties into Guice Context
        Names.bindProperties(binder(), properties);
        super.configure();
    }
  
}
