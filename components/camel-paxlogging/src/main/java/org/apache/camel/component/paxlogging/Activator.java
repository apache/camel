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
package org.apache.camel.component.paxlogging;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.camel.spi.ComponentResolver;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * The activator which register the ComponentResolver as a ServiceFactory.
 */
public class Activator implements BundleActivator {

    private ServiceRegistration registration;

    public void start(BundleContext bundleContext) throws Exception {
        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put("component", PaxLoggingComponent.NAME);
        registration = bundleContext.registerService(
                            ComponentResolver.class.getName(),
                            new PaxLoggingServiceFactory(),
                            props);
    }

    public void stop(BundleContext bundleContext) throws Exception {
        registration.unregister();
    }

}
