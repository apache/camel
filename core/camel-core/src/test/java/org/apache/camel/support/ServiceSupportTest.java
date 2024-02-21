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
package org.apache.camel.support;

import org.apache.camel.TestSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceSupportTest extends TestSupport {

    private static class MyService extends ServiceSupport {

        @Override
        protected void doStart() throws Exception {
        }

        @Override
        protected void doStop() throws Exception {
        }
    }

    @Test
    public void testServiceSupport() throws Exception {
        MyService service = new MyService();
        service.start();

        assertTrue(service.isStarted());
        assertFalse(service.isStarting());
        assertFalse(service.isStopped());
        assertFalse(service.isStopping());

        service.stop();

        assertTrue(service.isStopped());
        assertFalse(service.isStopping());
        assertFalse(service.isStarted());
        assertFalse(service.isStarting());
    }

    @Test
    public void testServiceSupportIsRunAllowed() throws Exception {
        MyService service = new MyService();
        assertFalse(service.isRunAllowed());

        service.start();
        assertTrue(service.isRunAllowed());

        // we are allowed to run while suspending/suspended
        service.suspend();
        assertTrue(service.isRunAllowed());
        service.resume();
        assertTrue(service.isRunAllowed());

        // but if we are stopped then we are not
        service.stop();
        assertFalse(service.isRunAllowed());
        service.shutdown();
        assertFalse(service.isRunAllowed());
    }

    private static class MyShutdownService extends ServiceSupport {

        private boolean shutdown;

        @Override
        protected void doStart() throws Exception {
        }

        @Override
        protected void doStop() throws Exception {
        }

        @Override
        protected void doShutdown() throws Exception {
            shutdown = true;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }
    }

    @Test
    public void testServiceSupportShutdown() throws Exception {
        MyShutdownService service = new MyShutdownService();
        service.start();

        assertTrue(service.isStarted());
        assertFalse(service.isStarting());
        assertFalse(service.isStopped());
        assertFalse(service.isStopping());
        assertFalse(service.isShutdown());

        service.shutdown();

        assertTrue(service.isStopped());
        assertFalse(service.isStopping());
        assertFalse(service.isStarted());
        assertFalse(service.isStarting());

        assertTrue(service.isShutdown());
    }

    @Test
    public void testExceptionOnStart() throws Exception {
        ServiceSupportTestExOnStart service = new ServiceSupportTestExOnStart();
        // forced not being stopped at start
        assertFalse(service.isStopped());
        try {
            service.start();
            fail("RuntimeException expected");
        } catch (RuntimeException e) {
            assertTrue(service.isStopped());
            assertFalse(service.isStopping());
            assertFalse(service.isStarted());
            assertFalse(service.isStarting());
        }
    }

    @Test
    public void testServiceBuild() throws Exception {
        MyService service = new MyService();
        assertTrue(service.isNew());
        service.build();
        assertTrue(service.isBuild());
        assertFalse(service.isInit());
        service.start();

        assertTrue(service.isStarted());
        assertFalse(service.isStarting());
        assertFalse(service.isStopped());
        assertFalse(service.isStopping());

        service.stop();

        assertTrue(service.isStopped());
        assertFalse(service.isStopping());
        assertFalse(service.isStarted());
        assertFalse(service.isStarting());
    }

    public static class ServiceSupportTestExOnStart extends ServiceSupport {

        public ServiceSupportTestExOnStart() {
            // just for testing force it to not be stopped
            status = SUSPENDED;
        }

        @Override
        protected void doStart() throws Exception {
            throw new RuntimeException("This service throws an exception when starting");
        }

        @Override
        protected void doStop() throws Exception {
        }

    }
}
