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
package org.apache.camel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SuspendableServiceTest {

    private static class MyService implements SuspendableService {

        private boolean suspended;

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void suspend() {
            suspended = true;
        }

        @Override
        public void resume() {
            suspended = false;
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }
    }

    @Test
    public void testSuspendable() {
        MyService my = new MyService();
        assertFalse(my.isSuspended());

        my.suspend();
        assertTrue(my.isSuspended());

        my.resume();
        assertFalse(my.isSuspended());
    }

}
