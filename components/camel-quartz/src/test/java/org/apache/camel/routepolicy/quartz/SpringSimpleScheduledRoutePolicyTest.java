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
package org.apache.camel.routepolicy.quartz;

import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;

public abstract class SpringSimpleScheduledRoutePolicyTest extends SpringScheduledRoutePolicyTest {

    public void setUp() {
        setApplicationContext(newAppContext("SimplePolicies.xml"));
        setTestType(TestType.SIMPLE);
    }

    private AbstractXmlApplicationContext newAppContext(String config) {
        return CamelSpringTestSupport.newAppContext(config, getClass());
    }
}

class Test1SpringScheduledRoutePolicyTest extends SpringSimpleScheduledRoutePolicyTest {
    @Test
    public void testScheduledStartRoutePolicy() throws Exception {
        startTest();
    }
}

class Test2SpringScheduledRoutePolicyTest extends SpringSimpleScheduledRoutePolicyTest {
    @Test
    public void testScheduledStopRoutePolicy() throws Exception {
        stopTest();
    }
}

class Test3SpringScheduledRoutePolicyTest extends SpringSimpleScheduledRoutePolicyTest {
    @Test
    public void testScheduledSuspendRoutePolicy() throws Exception {
        suspendTest();
    }
}

class Test4SpringScheduledRoutePolicyTest extends SpringSimpleScheduledRoutePolicyTest {
    @Test
    public void testScheduledResumeRoutePolicy() throws Exception {
        resumeTest();
    }
}
