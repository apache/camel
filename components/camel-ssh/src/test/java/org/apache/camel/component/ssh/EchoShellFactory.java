/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.camel.component.ssh;

import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import java.io.*;

/**
 * TODO Add javadoc
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class EchoShellFactory implements Factory<Command> {

    public Command create() {
        return new EchoShell();
    }

    protected static class EchoShell implements Command, Runnable {
        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;
        private Thread thread;

        public void setInputStream(InputStream in) {
            this.in = in;
        }

        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        public void start(Environment env) throws IOException {
            thread = new Thread(this, "EchoShell");
            thread.start();
        }

        public void destroy() {
            thread.interrupt();
        }

        public void run() {
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            try {
                for (;;) {
                    String s = r.readLine();
                    if (s == null) {
                        return;
                    }
                    out.write((s + "\n").getBytes());
                    out.flush();
                    if ("exit".equals(s)) {
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                callback.onExit(0);
            }
        }
    }
}
