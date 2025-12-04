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

package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.Printer;

public final class CommandHelper {

    private static final ThreadLocal<Printer> printerAssociation = new ThreadLocal<>();

    private CommandHelper() {}

    public static Printer getPrinter() {
        return printerAssociation.get();
    }

    public static void setPrinter(Printer out) {
        printerAssociation.set(out);
    }

    public static void cleanExportDir(String dir) {
        CommandHelper.cleanExportDir(dir, true);
    }

    public static void cleanExportDir(String dir, boolean keepHidden) {
        Path targetPath = Paths.get(dir);
        if (!Files.exists(targetPath)) {
            return;
        }

        try (Stream<Path> paths = Files.list(targetPath)) {
            paths.forEach(path -> {
                try {
                    boolean isHidden = Files.isHidden(path);
                    if (Files.isDirectory(path) && (!keepHidden || !isHidden)) {
                        PathUtils.deleteDirectory(path);
                    } else if (Files.isRegularFile(path) && (!keepHidden || !isHidden)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    // Ignore
                }
            });
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * A background task that reads from console, and can be used to signal when user has entered or pressed ctrl + c /
     * ctrl + d
     */
    public static class ReadConsoleTask implements Runnable {

        private final Runnable listener;

        public ReadConsoleTask(Runnable listener) {
            this.listener = listener;
        }

        @Override
        public void run() {
            if (System.console() != null) {
                System.console().readLine();
                listener.run();
            }
        }
    }
}
