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
package org.apache.camel.test.blueprint;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBException;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.MainSupport;
import org.apache.camel.view.ModelFileGenerator;
import org.osgi.framework.BundleContext;

/**
 * A command line tool for booting up a CamelContext using an OSGi Blueprint XML file
 */
public class Main extends MainSupport {

    private BundleContext bundleContext;
    private String descriptors = "OSGI-INF/blueprint/*.xml";
    private CamelContext camelContext;
    private String bundleName = "MyBundle";

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (bundleContext == null) {
            String descriptors = getDescriptors();
            if (descriptors == null) {
                throw new IllegalArgumentException("Descriptors must be provided, with the name of the blueprint XML file");
            }
            LOG.debug("Starting Blueprint XML file: " + descriptors);
            bundleContext = createBundleContext(bundleName);

            camelContext = CamelBlueprintHelper.getOsgiService(bundleContext, CamelContext.class);
            if (camelContext == null) {
                throw new IllegalArgumentException("Cannot find CamelContext in blueprint XML file: " + descriptors);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        LOG.debug("Stopping Blueprint XML file: " + descriptors);
        CamelBlueprintHelper.disposeBundleContext(bundleContext);
    }

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (camelContext != null) {
            return camelContext.createProducerTemplate();
        } else {
            return null;
        }
    }

    protected BundleContext createBundleContext() throws Exception {
        return createBundleContext(getClass().getSimpleName());
    }

    protected BundleContext createBundleContext(String name) throws Exception {
        return CamelBlueprintHelper.createBundleContext(name, descriptors);
    }

    @Override
    protected Map<String, CamelContext> getCamelContextMap() {
        Map<String, CamelContext> map = new HashMap<String, CamelContext>(1);
        if (camelContext != null) {
            map.put(camelContext.getName(), camelContext);
        }
        return map;
    }

    @Override
    protected ModelFileGenerator createModelFileGenerator() throws JAXBException {
        throw new UnsupportedOperationException("This method is not supported");
    }

    public String getDescriptors() {
        return descriptors;
    }

    public void setDescriptors(String descriptors) {
        this.descriptors = descriptors;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }
}
