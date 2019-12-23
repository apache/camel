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
package org.apache.camel.test.spring;

import org.apache.camel.ServiceStatus;
import org.apache.camel.util.StopWatch;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@UseAdviceWith
public class CamelSpringRunnerUseAdviceWithTest extends CamelSpringRunnerPlainTest {
    
    @Before
    public void testContextStarted() throws Exception {
        assertEquals(ServiceStatus.Stopped, camelContext.getStatus());
        camelContext.start();

        // just sleep a little to simulate testing take a bit time
        Thread.sleep(1000);
    }
    
    @Override
    @Test
    public void testStopwatch() {
        StopWatch stopWatch = StopWatchTestExecutionListener.getStopWatch();
        
        assertNotNull(stopWatch);
        long taken = stopWatch.taken();
        assertTrue(taken + " > 0, but was: " + taken, taken > 0);
        assertTrue(taken + " < 3000, but was: " + taken, taken < 3000);
    }
}
