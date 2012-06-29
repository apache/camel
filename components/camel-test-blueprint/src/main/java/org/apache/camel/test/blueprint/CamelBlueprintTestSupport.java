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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;

/**
 * Base class for OSGi Blueprint unit tests with Camel.
 */
public abstract class CamelBlueprintTestSupport extends CamelTestSupport {

    private BundleContext bundleContext;

    @Before
    @Override
    public void setUp() throws Exception {
        String symbolicName = getClass().getSimpleName();
        this.bundleContext = CamelBlueprintHelper.createBundleContext(symbolicName, getBlueprintDescriptor(),
                true, getBundleFilter(), getBundleVersion());

        // must register override properties early in OSGi containers
        Properties extra = useOverridePropertiesWithPropertiesComponent();
        if (extra != null) {
            bundleContext.registerService(PropertiesComponent.OVERRIDE_PROPERTIES, extra, null);
        }

        super.setUp();

        // must wait for blueprint container to be published then the namespace parser is complete and we are ready for testing
        log.debug("Waiting for BlueprintContainer to be published with symbolicName: {}", symbolicName);
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=" + symbolicName + ")");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        CamelBlueprintHelper.disposeBundleContext(bundleContext);
    }

    /**
     * Gets the bundle descriptor from the classpath.
     * <p/>
     * Return the location(s) of the bundle descriptors from the classpath.
     * Separate multiple locations by comma, or return a single location.
     * <p/>
     * For example override this method and return <tt>OSGI-INF/blueprint/camel-context.xml</tt>
     *
     * @return the location of the bundle descriptor file.
     */
    protected String getBlueprintDescriptor() {
        return null;
    }

    /**
     * Gets filter expression of bundle descriptors.
     * Modify this method if you wish to change default behavior.
     *
     * @return filter expression for OSGi bundles.
     */
    protected String getBundleFilter() {
        return CamelBlueprintHelper.BUNDLE_FILTER;
    }

    /**
     * Gets test bundle version.
     * Modify this method if you wish to change default behavior.
     *
     * @return test bundle version
     */
    protected String getBundleVersion() {
        return CamelBlueprintHelper.BUNDLE_VERSION;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = CamelBlueprintHelper.getOsgiService(bundleContext, CamelContext.class);
        // must override context so we use the correct one in testing
        context = (ModelCamelContext) answer;
        return answer;
    }

    protected <T> T getOsgiService(Class<T> type) {
        return CamelBlueprintHelper.getOsgiService(bundleContext, type);
    }

    protected <T> T getOsgiService(Class<T> type, long timeout) {
        return CamelBlueprintHelper.getOsgiService(bundleContext, type, timeout);
    }

    protected <T> T getOsgiService(Class<T> type, String filter) {
        return CamelBlueprintHelper.getOsgiService(bundleContext, type, filter);
    }

    protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
        return CamelBlueprintHelper.getOsgiService(bundleContext, type, filter, timeout);
    }

}


