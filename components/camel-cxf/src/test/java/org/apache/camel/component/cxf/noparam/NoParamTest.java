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
package org.apache.camel.component.cxf.noparam;

import org.apache.camel.ExchangePattern;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class NoParamTest extends CamelSpringTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoParamTest.class);
    @BeforeClass
    public static void loadTestSupport() {
        // Need to load the static class first
        CXFTestSupport.getPort1();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/noparam/camel-route.xml");
    }

    @Test
    public void testNullBodoy() throws Exception {
        Object body = template.sendBody("direct:noParam", ExchangePattern.InOut, null);
        LOGGER.error(body.toString());
    }

}
