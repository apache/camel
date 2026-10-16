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
package org.apache.camel.dsl.jbang.core.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs an external command, such as <tt>docker info</tt>, with a timeout that holds: the output is read while the
 * command runs (reading it to the end first waits for the command, however long it takes), and a command that does not
 * finish in time is destroyed.
 */
public final class ExternalCommand {

    private ExternalCommand() {
    }

    /**
     * Whether the command ends with exit code 0 within the timeout. Its output is discarded.
     */
    public static boolean succeeds(long timeoutMillis, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            // no such command
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return false;
        }
    }

    /**
     * The output of the command (stdout and stderr), when it ends with exit code 0 within the timeout.
     *
     * @return the output, or <tt>null</tt> when the command does not exist, fails or does not finish in time
     */
    public static String output(long timeoutMillis, List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = process.getInputStream();
            Thread reader = new Thread(() -> {
                try (in) {
                    in.transferTo(out);
                } catch (IOException e) {
                    // the command was destroyed
                }
            }, "camel-external-command");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            // the rest of the output, once the command has ended
            reader.join(TimeUnit.SECONDS.toMillis(1));
            if (process.exitValue() != 0) {
                return null;
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return null;
        }
    }
}
