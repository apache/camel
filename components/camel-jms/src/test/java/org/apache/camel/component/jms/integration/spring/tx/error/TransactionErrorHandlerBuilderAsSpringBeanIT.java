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

package org.apache.camel.component.jms.integration.spring.tx.error;

import org.apache.camel.component.jms.integration.spring.AbstractSpringJMSITSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * To demonstrate the TransactionErrorHandlerBuilder configured in Spring XML.
 */
@Tags({@Tag("not-parallel"), @Tag("spring"), @Tag("tx")})
public class TransactionErrorHandlerBuilderAsSpringBeanIT extends AbstractSpringJMSITSupport {

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/component/jms/integration/spring/tx/error/TransactionErrorHandlerBuilderAsSpringBeanIT.xml");
    }

    @Test
    public void testTransactionSuccess() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Bye World");
        // success at 3rd attempt
        mock.message(0).header("count").isEqualTo(3);

        template.sendBody("activemq:queue:okay.TransactionErrorHandlerBuilderAsSpringBeanTest", "Hello World");

        mock.assertIsSatisfied();
    }
}
