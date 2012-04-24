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
package org.apache.camel.cdi;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cdi.CdiCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.deltaspike.cdise.api.CdiContainer;
import org.apache.deltaspike.cdise.api.CdiContainerLoader;
import org.junit.After;
import org.junit.Before;

/**
 * Base class for cdi tests.
 */
public abstract class CamelCdiTestContainer extends CamelTestSupport {

    private CdiContainer cdiContainer;

    @Before
    public void setUp() throws Exception {
        // set up CDI container before camel context start up
        cdiContainer = CdiContainerLoader.getCdiContainer();
        cdiContainer.boot();

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        context.stop();
        cdiContainer.shutdown();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
       return new CdiCamelContext();
    }
}