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
package org.apache.camel.guice;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.apache.camel.CamelContext;
import org.apache.camel.guice.inject.Injectors;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;

/**
 * @version
 */
public class GuiceTest extends TestSupport {

    /**
     * Asserts that the CamelContext is available in the given Injector, that
     * its been started, then close the injector
     * 
     * @param injector
     */
    public static void assertCamelContextRunningThenCloseInjector(Injector injector) throws Exception {
        CamelContext camelContext = injector.getInstance(CamelContext.class);

        org.hamcrest.MatcherAssert.assertThat(camelContext, org.hamcrest.Matchers.is(GuiceCamelContext.class));
        GuiceCamelContext guiceContext = (GuiceCamelContext)camelContext;
        assertTrue("is started!", guiceContext.isStarted());

        Thread.sleep(1000);

        Injectors.close(injector);
    }

    public static class Cheese {
        private final CamelContext camelContext;

        @Inject
        public Cheese(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        public CamelContext getCamelContext() {
            return camelContext;
        }
    }

    @Test
    public void testGuice() throws Exception {
        // lets disable resource injection to avoid JNDI being used
        Injector injector = Guice.createInjector(new CamelModuleWithMatchingRoutes());

        Cheese cheese = injector.getInstance(Cheese.class);
        assertNotNull("Should have cheese", cheese);
        assertNotNull("Should have camelContext", cheese.getCamelContext());
        log.info("Got " + cheese);

        assertCamelContextRunningThenCloseInjector(injector);
    }

}