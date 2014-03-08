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

package org.apache.camel.component.cxf;

import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.BusFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CxfBlueprintEndpoint extends CxfEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(CxfBlueprintEndpoint.class);

    private BlueprintContainer blueprintContainer;
    private BundleContext bundleContext;
    private BlueprintCamelContext blueprintCamelContext;

    public CxfBlueprintEndpoint(String address, BundleContext context) {
        super(address, (CxfComponent)null);
        bundleContext = context;
    }

    public void destroy() {
        // Clean up the BusFactory's defaultBus
        // This method is not called magically, blueprint
        // needs you to set the destroy-method.
        BusFactory.setDefaultBus(null);
        BusFactory.setThreadDefaultBus(null);
    }
    
    public void setServiceClass(String n) throws ClassNotFoundException {
        setServiceClass(bundleContext.getBundle().loadClass(n));
    }
    
    // Package private methods
    // -------------------------------------------------------------------------


    protected void checkName(Object value, String name) {
        if (ObjectHelper.isEmpty(value)) {
            LOG.warn("The " + name + " of " + this.getEndpointUri() + " is empty, cxf will try to load the first one in wsdl for you.");
        }
    }

    public BlueprintContainer getBlueprintContainer() {
        return blueprintContainer;
    }

    public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public BlueprintCamelContext getBlueprintCamelContext() {
        return blueprintCamelContext;
    }

    public void setBlueprintCamelContext(BlueprintCamelContext blueprintCamelContext) {
        this.blueprintCamelContext = blueprintCamelContext;
    }

    public CxfBlueprintEndpoint getBean() {
        return this;
    }
}
