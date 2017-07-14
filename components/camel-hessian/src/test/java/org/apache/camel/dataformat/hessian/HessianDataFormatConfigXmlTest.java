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
package org.apache.camel.dataformat.hessian;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class HessianDataFormatConfigXmlTest extends CamelSpringTestSupport {

    @Test
    public void testMarshalAndUnmarshalString() throws Exception {
        final String object = "This is a string";

        final MockEndpoint mock = context().getEndpoint("mock:reverse", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(object.getClass());
        mock.message(0).body().isEqualTo(object);

        final Object marshalled = template.requestBody("direct:in", object);
        template.sendBody("direct:back", marshalled);

        mock.assertIsSatisfied();

    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/hessian/HessianTestConfig.xml");
    }
}
