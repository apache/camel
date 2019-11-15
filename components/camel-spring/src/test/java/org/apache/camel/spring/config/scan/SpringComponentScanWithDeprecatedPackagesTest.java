/*
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
package org.apache.camel.spring.config.scan;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringComponentScanWithDeprecatedPackagesTest extends ContextTestSupport {

    private AbstractApplicationContext applicationContext;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/scan/componentScanWithPackages.xml");
        context = applicationContext.getBean("camelContext", ModelCamelContext.class);
        template = context.createProducerTemplate();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        // we're done so let's properly close the application context
        IOHelper.close(applicationContext);

        super.tearDown();
    }

    @Test
    public void testSpringComponentScanFeature() throws InterruptedException {
        template.sendBody("direct:start", "request");
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
    }
}
