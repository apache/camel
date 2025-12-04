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

package org.apache.camel.component.ssh;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

public class ExitCodeCommandFactory extends EchoCommandFactory {

    @Override
    public Command createCommand(ChannelSession channelSession, String command) {
        return new ExitCodeCommand(command);
    }

    public static class ExitCodeCommand extends EchoCommand {

        private static final AtomicInteger exit = new AtomicInteger();

        public ExitCodeCommand(String command) {
            super(command);
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            super.setExitCallback((i, s, b) -> {
                callback.onExit(exit.incrementAndGet());
            });
        }
    }
}
