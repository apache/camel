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

import java.util.logging.LogManager;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.internal.CamelExtension;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.deltaspike.cdise.api.CdiContainer;
import org.apache.deltaspike.cdise.api.CdiContainerLoader;
import org.apache.deltaspike.core.api.provider.BeanProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 * Base class for cdi tests.
 */
public abstract class CdiContextTestSupport extends CamelTestSupport {

    private CdiContainer cdiContainer;

    /**
     * Reset configuration of java util logging and forward it to slf4j.
     * 
     * @throws Exception In case of failures.
     */
    @BeforeClass
    public static void before() throws Exception {
        LogManager.getLogManager().reset();
        org.slf4j.bridge.SLF4JBridgeHandler.install();
    }

    @Before
    public void setUp() throws Exception {
        // set up CDI container before camel context start up
        cdiContainer = CdiContainerLoader.getCdiContainer();
        cdiContainer.boot();

        // inject fields inside child classes
        BeanProvider.injectFields(this);

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        cdiContainer.shutdown();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return BeanProvider.getContextualReference(CdiCamelContext.class);
    }

    @Override
    protected void applyCamelPostProcessor() throws Exception {
        // lets perform any custom camel injection on the test case object
        CamelExtension camelExtension = BeanProvider.getContextualReference(CamelExtension.class);
        camelExtension.inject(this);
    }
}

