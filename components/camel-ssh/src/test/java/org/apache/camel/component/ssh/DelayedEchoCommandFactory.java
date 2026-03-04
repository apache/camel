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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;

/**
 * A command factory that introduces a delay before echoing the command back. Used to test idle timeout behavior — if
 * the client's idle timeout is shorter than the delay, the session will be closed before the command completes.
 */
public class DelayedEchoCommandFactory implements CommandFactory {

    private final long delayMs;

    public DelayedEchoCommandFactory(long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public Command createCommand(ChannelSession channelSession, String command) {
        return new DelayedEchoCommand(command, delayMs);
    }

    protected static class DelayedEchoCommand implements Command, Runnable {
        private final String command;
        private final long delayMs;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;
        private Thread thread;

        public DelayedEchoCommand(String command, long delayMs) {
            this.command = command;
            this.delayMs = delayMs;
        }

        @Override
        public void setInputStream(InputStream in) {
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        @Override
        public void start(ChannelSession channelSession, Environment environment) throws IOException {
            thread = new Thread(this, "DelayedEchoCommand");
            thread.start();
        }

        @Override
        public void destroy(ChannelSession channelSession) throws Exception {
            // noop
        }

        @Override
        public void run() {
            boolean succeeded = true;
            String message = null;
            try {
                Thread.sleep(delayMs);
                err.write("Error:".getBytes());
                err.write(command.getBytes());
                err.flush();
                out.write(command.getBytes());
                out.flush();
            } catch (Exception e) {
                succeeded = false;
                message = e.toString();
            } finally {
                if (succeeded) {
                    callback.onExit(0);
                } else {
                    callback.onExit(1, message);
                }
            }
        }
    }
}
