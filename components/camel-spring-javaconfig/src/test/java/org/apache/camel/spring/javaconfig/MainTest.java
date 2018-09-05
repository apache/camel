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
package org.apache.camel.spring.javaconfig;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

public class MainTest extends Assert {

    @Test
    public void  testOptions() throws Exception {
        CamelContext context = createCamelContext(new String[] {"-cc", "org.apache.camel.spring.javaconfig.config.ContextConfig"});
        context.start();
        runTests(context);
        context.stop();
    }

    @Test
    public void testOptionBP() throws Exception {
        CamelContext context = createCamelContext(new String[]{"-bp", "org.apache.camel.spring.javaconfig.config"});
        context.start();
        runTests(context);
        context.stop();
    }

    private CamelContext createCamelContext(String[] options) throws Exception {
        Main main = new Main();
        main.parseArguments(options);
        ApplicationContext applicationContext = main.createDefaultApplicationContext();
        CamelContext context = applicationContext.getBean(CamelContext.class);
        return context;
    }

    private void runTests(CamelContext context) throws Exception {
        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        ProducerTemplate template = context.createProducerTemplate();

        String expectedBody = "<matched/>";
        resultEndpoint.expectedBodiesReceived(expectedBody);
        template.sendBodyAndHeader("direct:start", expectedBody, "foo", "bar");
        resultEndpoint.assertIsSatisfied();

        resultEndpoint.reset();
        resultEndpoint.expectedMessageCount(0);
        template.sendBodyAndHeader("direct:start", "<notMatched/>", "foo", "notMatchedHeaderValue");
        resultEndpoint.assertIsSatisfied();
    }

}
