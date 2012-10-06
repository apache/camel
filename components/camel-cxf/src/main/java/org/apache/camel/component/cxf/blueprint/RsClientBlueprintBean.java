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
package org.apache.camel.component.cxf.blueprint;

import java.util.HashMap;

import org.apache.camel.component.cxf.NullFaultListener;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.logging.FaultListener;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;

public class RsClientBlueprintBean extends JAXRSClientFactoryBean implements BlueprintSupport, Cloneable {
    private BlueprintContainer blueprintContainer;
    private BundleContext bundleContext;
    private int loggingSizeLimit;
    private LoggingFeature loggingFeature;
    
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
    
    public boolean isLoggingFeatureEnabled() {
        return loggingFeature != null;
    }

    public void setLoggingFeatureEnabled(boolean loggingFeatureEnabled) {
        if (loggingFeature != null) {
            getFeatures().remove(loggingFeature);
            loggingFeature = null;
        }
        if (loggingFeatureEnabled) {
            if (getLoggingSizeLimit() > 0) {
                loggingFeature = new LoggingFeature(getLoggingSizeLimit());
            } else {
                loggingFeature = new LoggingFeature();
            }
            getFeatures().add(loggingFeature);
        }
        
    }

    public int getLoggingSizeLimit() {
        return loggingSizeLimit;
    }

    public void setLoggingSizeLimit(int loggingSizeLimit) {
        this.loggingSizeLimit = loggingSizeLimit;
        if (loggingFeature != null) {
            getFeatures().remove(loggingFeature);
            if (loggingSizeLimit > 0) {
                loggingFeature = new LoggingFeature(loggingSizeLimit);
            } else {
                loggingFeature = new LoggingFeature();
            }
            getFeatures().add(loggingFeature);
        }
    }
    
    public void setSkipFaultLogging(boolean skipFaultLogging) {
        if (skipFaultLogging) {
            if (this.getProperties() == null) {
                this.setProperties(new HashMap<String, Object>());
            }
            this.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
        }
    }

}
