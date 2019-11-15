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
package org.apache.camel.spring.javaconfig.test;

import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {MyConfig.class}, loader = CamelSpringDelegatingTestContextLoader.class)
@Component
public class JavaConfigWithPostProcessorTest extends AbstractJUnit4SpringContextTests implements Cheese {
    private boolean doCheeseCalled;

    @Test
    public void testPostProcessorInjectsMe() throws Exception {
        assertEquals("doCheese() should be called", true, doCheeseCalled);
    }

    @Override
    public void doCheese() {
        logger.info("doCheese called!");
        doCheeseCalled = true;
    }
}
