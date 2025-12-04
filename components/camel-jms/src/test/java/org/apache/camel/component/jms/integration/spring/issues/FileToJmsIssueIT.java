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

package org.apache.camel.component.jms.integration.spring.issues;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.jms.integration.spring.SpringJMSBasic;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;

@Tags({@Tag("not-parallel"), @Tag("spring")})
public class FileToJmsIssueIT extends SpringJMSBasic {

    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/jms/integration/spring/issues/FileToJmsIssueIT.xml");
    }

    @Test
    void testFileToJms() throws Exception {
        result.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(
                "file://target/jmsfile?fileExist=Override", "Hello World", Exchange.FILE_NAME, "hello.txt");

        result.assertIsSatisfied();
    }
}
