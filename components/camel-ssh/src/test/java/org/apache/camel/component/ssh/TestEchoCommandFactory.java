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
package org.apache.camel.component.ssh;

import java.util.concurrent.CountDownLatch;

import org.apache.sshd.server.Command;

public class TestEchoCommandFactory extends EchoCommandFactory {

    @Override
    public Command createCommand(String command) {
        return new TestEchoCommand(command);
    }

    public static class TestEchoCommand extends EchoCommand {
        public static CountDownLatch latch = new CountDownLatch(1);

        public TestEchoCommand(String command) {
            super(command);
        }

        @Override
        public void destroy() {
            if (latch != null) {
                latch.countDown();
            }
            super.destroy();
        }
    }
}