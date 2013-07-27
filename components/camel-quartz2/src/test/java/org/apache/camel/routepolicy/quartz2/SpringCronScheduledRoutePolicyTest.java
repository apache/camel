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
package org.apache.camel.routepolicy.quartz2;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringCronScheduledRoutePolicyTest extends SpringScheduledRoutePolicyTest {

    @Before
    public void setUp() {
        setApplicationContext(new ClassPathXmlApplicationContext("org/apache/camel/routepolicy/quartz2/CronPolicies.xml"));
        setTestType(TestType.CRON);
    }
    
    @Test
    public void testScheduledStartRoutePolicy() throws Exception {
        startTest();
    }

    @Test
    public void testScheduledStopRoutePolicy() throws Exception {
        stopTest();
    }
    
    @Test
    public void testScheduledSuspendRoutePolicy() throws Exception {
        suspendTest();
    }
    
    @Test
    public void testScheduledResumeRoutePolicy() throws Exception {
        resumeTest();
    }
    
}
