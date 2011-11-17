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
package org.apache.camel.impl;

import org.apache.camel.TestSupport;

/**
 * @version 
 */
public class ServiceSupportTest extends TestSupport {

    private class MyService extends ServiceSupport {

        protected void doStart() throws Exception {
        }

        protected void doStop() throws Exception {
        }
    }

    public void testServiceSupport() throws Exception {
        MyService service = new MyService();
        service.start();

        assertEquals(true, service.isStarted());
        assertEquals(false, service.isStarting());
        assertEquals(false, service.isStopped());
        assertEquals(false, service.isStopping());

        service.stop();

        assertEquals(true, service.isStopped());
        assertEquals(false, service.isStopping());
        assertEquals(false, service.isStarted());
        assertEquals(false, service.isStarting());
    }

    private class MyShutdownService extends ServiceSupport {

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

        public boolean isShutdown() {
            return shutdown;
        }
    }

    public void testServiceSupportShutdown() throws Exception {
        MyShutdownService service = new MyShutdownService();
        service.start();

        assertEquals(true, service.isStarted());
        assertEquals(false, service.isStarting());
        assertEquals(false, service.isStopped());
        assertEquals(false, service.isStopping());
        assertEquals(false, service.isShutdown());

        service.shutdown();

        assertEquals(true, service.isStopped());
        assertEquals(false, service.isStopping());
        assertEquals(false, service.isStarted());
        assertEquals(false, service.isStarting());

        assertEquals(true, service.isShutdown());
    }


}
