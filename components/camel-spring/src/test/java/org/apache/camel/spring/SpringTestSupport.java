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
package org.apache.camel.spring;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.util.ObjectHelper;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision$
 */
public abstract class SpringTestSupport extends ContextTestSupport {
    protected AbstractXmlApplicationContext applicationContext;

    protected abstract ClassPathXmlApplicationContext createApplicationContext();

    @Override
    protected void setUp() throws Exception {
        applicationContext = createApplicationContext();
        assertNotNull("Should have created a valid spring context", applicationContext);

        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (applicationContext != null) {
            applicationContext.destroy();
        }
    }


    /**
     * Looks up the mandatory spring bean of the given name and type, failing if
     * it is not present or the correct type
     */
    public <T> T getMandatoryBean(Class<T> type, String name) {
        Object value = applicationContext.getBean(name);
        assertNotNull("No spring bean found for name <" + name + ">", value);
        if (type.isInstance(value)) {
            return type.cast(value);
        } else {
            fail("Spring bean <" + name + "> is not an instanceof " + type.getName() + " but is of type " + ObjectHelper.className(value));
            return null;
        }
    }

    @Override
    protected void assertValidContext(CamelContext context) {
        super.assertValidContext(context);

        List<Route> routes = context.getRoutes();
        int routeCount = getExpectedRouteCount();
        if (routeCount > 0) {
            assertNotNull("Should have some routes defined", routes);
            assertTrue("Should have at least one route", routes.size() >= routeCount);
        }
        log.debug("Camel Routes: " + routes);
    }

    protected int getExpectedRouteCount() {
        return 1;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext);
    }
}
