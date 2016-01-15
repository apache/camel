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
package org.apache.camel.component.beanstalk;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EndpointTest {
    CamelContext context;

    @Before
    public void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.disableJMX();
        context.start();
    }

    @Test
    public void testPriority() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:default?jobPriority=1000", BeanstalkEndpoint.class);
        assertNotNull("Beanstalk endpoint", endpoint);
        assertEquals("Priority", 1000, endpoint.getJobPriority());
    }

    @Test
    public void testTimeToRun() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:default?jobTimeToRun=10", BeanstalkEndpoint.class);
        assertNotNull("Beanstalk endpoint", endpoint);
        assertEquals("Time to run", 10, endpoint.getJobTimeToRun());
    }

    @Test
    public void testDelay() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:default?jobDelay=10", BeanstalkEndpoint.class);
        assertNotNull("Beanstalk endpoint", endpoint);
        assertEquals("Delay", 10, endpoint.getJobDelay());
    }

    @Test
    public void testCommand() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:default?command=release", BeanstalkEndpoint.class);
        assertNotNull("Beanstalk endpoint", endpoint);
        assertEquals("Command", BeanstalkComponent.COMMAND_RELEASE, endpoint.getCommand().name());
    }

    @Test
    public void testTubes() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:host:11303/tube1+tube%2B+tube%3F?command=kick", BeanstalkEndpoint.class);
        assertNotNull("Beanstalk endpoint", endpoint);
        assertEquals("Command", BeanstalkComponent.COMMAND_KICK, endpoint.getCommand().name());
        assertEquals("Host", "host", endpoint.conn.host);
        assertArrayEquals("Tubes", new String[]{"tube1", "tube+", "tube?"}, endpoint.conn.tubes);
    }

    @After
    public void tearDown() throws Exception {
        context.stop();
    }
}
