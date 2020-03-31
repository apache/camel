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
package org.apache.camel.component.beanstalk;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EndpointTest {
    CamelContext context;

    @BeforeEach
    public void setUp() throws Exception {
        context = new DefaultCamelContext(false);
        context.disableJMX();
        context.start();
    }

    @Test
    void testPriority() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:default?jobPriority=1000", BeanstalkEndpoint.class);
        assertNotNull(endpoint, "Beanstalk endpoint");
        assertEquals(1000, endpoint.getJobPriority(), "Priority");
    }

    @Test
    void testTimeToRun() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:default?jobTimeToRun=10", BeanstalkEndpoint.class);
        assertNotNull(endpoint, "Beanstalk endpoint");
        assertEquals(10, endpoint.getJobTimeToRun(), "Time to run");
    }

    @Test
    void testDelay() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:default?jobDelay=10", BeanstalkEndpoint.class);
        assertNotNull(endpoint, "Beanstalk endpoint");
        assertEquals(10, endpoint.getJobDelay(), "Delay");
    }

    @Test
    void testCommand() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:default?command=release", BeanstalkEndpoint.class);
        assertNotNull(endpoint, "Beanstalk endpoint");
        assertEquals(BeanstalkComponent.COMMAND_RELEASE, endpoint.getCommand().name(), "Command");
    }

    @Test
    void testTubes() {
        BeanstalkEndpoint endpoint = context.getEndpoint("beanstalk:host:11303/tube1+tube%2B+tube%3F?command=kick", BeanstalkEndpoint.class);
        assertNotNull(endpoint, "Beanstalk endpoint");
        assertEquals(BeanstalkComponent.COMMAND_KICK, endpoint.getCommand().name(), "Command");
        assertEquals("host", endpoint.conn.host, "Host");
        assertArrayEquals(new String[]{"tube1", "tube+", "tube?"}, endpoint.conn.tubes, "Tubes");
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }
}
