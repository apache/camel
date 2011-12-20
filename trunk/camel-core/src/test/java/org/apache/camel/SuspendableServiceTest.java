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
package org.apache.camel;

import junit.framework.TestCase;

/**
 * @version 
 */
public class SuspendableServiceTest extends TestCase {

    private static class MyService implements SuspendableService {

        private boolean suspended;

        public void start() throws Exception {
        }

        public void stop() throws Exception {
        }

        public void suspend() {
            suspended = true;
        }

        public void resume() {
            suspended = false;
        }

        public boolean isSuspended() {
            return suspended;
        }
    }

    public void testSuspendable() {
        MyService my = new MyService();
        assertEquals(false, my.isSuspended());

        my.suspend();
        assertEquals(true, my.isSuspended());

        my.resume();
        assertEquals(false, my.isSuspended());
    }

}
