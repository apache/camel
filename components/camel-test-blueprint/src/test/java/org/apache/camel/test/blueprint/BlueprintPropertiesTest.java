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

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.BlueprintEvent;

/**
 *
 */
public class BlueprintPropertiesTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/configadmin.xml";
    }

    @Test
    public void testProperties() throws Exception {
        Bundle camelCore = getBundleBySymbolicName("org.apache.camel.camel-core");
        Bundle test = getBundleBySymbolicName(getClass().getSimpleName());

        camelCore.stop();
        test.stop();

        Thread.sleep(500);

        test.start();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=" + getClass().getSimpleName() + ")", 500);
            fail("Expected a timeout");
        } catch (RuntimeException e) {
            // Expected timeout
        }

        CamelBlueprintHelper.waitForBlueprintContainer(null, test.getBundleContext(), getClass().getSimpleName(), BlueprintEvent.CREATED,
                new Runnable() {
            @Override
            public void run() {
                try {
                    camelCore.start();
                } catch (BundleException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        });
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=" + getClass().getSimpleName() + ")", 500);
    }

    private Bundle getBundleBySymbolicName(String name) {
        for (Bundle bundle : getBundleContext().getBundles()) {
            if (bundle.getSymbolicName().equals(name)) {
                return bundle;
            }
        }
        return null;
    }

}
