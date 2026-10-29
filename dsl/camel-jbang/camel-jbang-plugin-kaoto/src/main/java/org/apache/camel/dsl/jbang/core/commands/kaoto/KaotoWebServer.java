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
package org.apache.camel.dsl.jbang.core.commands.kaoto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;

class KaotoWebServer {

    @FunctionalInterface
    interface CommandLauncher {
        Process launch(Path logFile, String... args) throws IOException;
    }

    interface ProcessManager {
        void launch(String key, Path logFile, String... args) throws IOException;

        boolean isRunning(String key);

        boolean stop(String key);
    }

    private final int port;
    private final Path sourceDir;
    private final CountDownLatch shutdownLatch;
    private final CommandLauncher launcher;
    private final ConcurrentHashMap<String, Process> processes = new ConcurrentHashMap<>();
    private Vertx vertx;
    private HttpServer server;
    private boolean stopped;

    KaotoWebServer(int port, Path sourceDir, CountDownLatch shutdownLatch) {
        this(port, sourceDir, shutdownLatch,
             (logFile, args) -> launchCamelProcess(sourceDir, args));
    }

    KaotoWebServer(int port, Path sourceDir, CountDownLatch shutdownLatch, CommandLauncher launcher) {
        this.port = port;
        this.sourceDir = sourceDir;
        this.shutdownLatch = shutdownLatch;
        this.launcher = launcher;
    }

    void start() throws IOException {
        vertx = Vertx.vertx();

        Router router = Router.router(vertx);

        router.route().handler(ctx -> {
            ctx.response().putHeader("X-Frame-Options", "DENY");
            ctx.response().putHeader("Content-Security-Policy", "frame-ancestors 'none'");
            ctx.next();
        });

        ProcessManager processManager = new ProcessManager() {
            @Override
            public void launch(String key, Path logFile, String... args) throws IOException {
                stop(key);
                Process process = launcher.launch(logFile, args);
                processes.put(key, process);
                monitorProcess(process, logFile);
            }

            @Override
            public boolean isRunning(String key) {
                Process p = processes.get(key);
                return p != null && p.isAlive();
            }

            @Override
            public boolean stop(String key) {
                Process p = processes.get(key);
                if (p != null && p.isAlive()) {
                    p.descendants().forEach(ProcessHandle::destroy);
                    p.destroy();
                    return true;
                }
                return false;
            }
        };

        KaotoRequestHandler.registerRoutes(router, sourceDir, shutdownLatch, processManager);

        HttpServerOptions options = new HttpServerOptions()
                .setHost("127.0.0.1")
                .setPort(port);

        server = vertx.createHttpServer(options);
        try {
            server.requestHandler(router).listen().toCompletionStage().toCompletableFuture().get();
        } catch (Exception e) {
            stop();
            Throwable cause = unwrap(e);
            if (cause instanceof BindException bindException) {
                throw bindException;
            }
            throw new IOException("Failed to start Kaoto server", cause);
        }
    }

    synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        for (Process p : processes.values()) {
            if (p.isAlive()) {
                p.descendants().forEach(ProcessHandle::destroy);
                p.destroy();
            }
        }
        processes.clear();
        if (server != null) {
            try {
                server.close().toCompletionStage().toCompletableFuture().get();
            } catch (Exception e) {
                // best effort
            }
        }
        if (vertx != null) {
            try {
                vertx.close().toCompletionStage().toCompletableFuture().get();
            } catch (Exception e) {
                // best effort
            }
        }
    }

    static Process launchCamelProcess(Path sourceDir, String... args) throws IOException {
        List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
        Collections.addAll(cmd, args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(sourceDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static void monitorProcess(Process process, Path logFile) {
        Thread monitor = new Thread(() -> {
            try (InputStream is = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    if (logFile != null) {
                        appendToLogFile(logFile, buf, n);
                    }
                }
                int exitCode = process.waitFor();
                if (exitCode != 0 && logFile != null) {
                    byte[] msg = (System.lineSeparator() + "Process exited with code " + exitCode
                                  + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8);
                    appendToLogFile(logFile, msg, msg.length);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // best effort
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }

    private static void appendToLogFile(Path logFile, byte[] data, int length) {
        try {
            Files.createDirectories(logFile.getParent());
            try (OutputStream os = Files.newOutputStream(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                os.write(data, 0, length);
            }
        } catch (IOException e) {
            // best effort
        }
    }

    private static Throwable unwrap(Throwable t) {
        while (t.getCause() != null && t != t.getCause()) {
            t = t.getCause();
        }
        return t;
    }
}
