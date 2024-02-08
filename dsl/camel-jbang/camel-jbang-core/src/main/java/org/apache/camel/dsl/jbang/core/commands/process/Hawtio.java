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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.awt.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.tooling.maven.MavenArtifact;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "hawtio", description = "Launch Hawtio web console", sortOptions = false)
public class Hawtio extends CamelCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name;

    @CommandLine.Option(names = { "--version" },
                        description = "Version of the Hawtio web console", defaultValue = "3.0.1")
    String version = "3.0.1";

    // use port 8888 as 8080 is too commonly used
    @CommandLine.Option(names = { "--port" },
                        description = "Port number to use for Hawtio web console (port 8888 by default)", defaultValue = "8888")
    int port = 8888;

    @CommandLine.Option(names = { "--openUrl" },
                        description = "To automatic open Hawtio web console in the web browser", defaultValue = "true")
    boolean openUrl = true;

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private volatile long pid;

    public Hawtio(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        int exit;
        if (name == null) {
            exit = callHawtio();
        } else {
            // attach jolokia before calling hawtio and disconnect afterwards
            try {
                exit = connectJolokia();
                if (exit == 0) {
                    exit = callHawtio();
                }
            } finally {
                disconnectJolokia();
            }
        }
        return exit;
    }

    protected Integer connectJolokia() throws Exception {
        Jolokia jolokia = new Jolokia(getMain());
        jolokia.name = name;
        int exit = jolokia.call();
        this.pid = jolokia.getPid();
        return exit;
    }

    protected void disconnectJolokia() throws Exception {
        Jolokia jolokia = new Jolokia(getMain());
        jolokia.name = Long.toString(pid);
        jolokia.stop = true;
        jolokia.call();
    }

    protected Integer callHawtio() throws Exception {
        ClassLoader cl = createClassLoader();

        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(cl);
        downloader.start();
        // download hawtio embedded mode
        downloader.downloadDependency("io.hawt", "hawtio-embedded", version);
        // download war that has the web-console
        MavenArtifact ma = downloader.downloadArtifact("io.hawt", "hawtio-war:war", version);
        if (ma == null) {
            System.err.println("Cannot download io.hawt:hawtio-war:war:" + version);
            return 1;
        }

        String war = ma.getFile().getAbsolutePath();

        // invoke hawtio main app that launches hawtio
        try {
            // turn off hawito auth
            System.setProperty("hawtio.authenticationEnabled", "false");

            // use CL from that has the downloaded JAR
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> clazz = cl.loadClass("io.hawt.embedded.Main");
            Object hawt = clazz.getDeclaredConstructor().newInstance();
            Method m = clazz.getMethod("setWar", String.class);
            ObjectHelper.invokeMethod(m, hawt, war);
            m = clazz.getMethod("setPort", Integer.class);
            ObjectHelper.invokeMethod(m, hawt, port);
            m = clazz.getMethod("run");
            ObjectHelper.invokeMethod(m, hawt);

            if (openUrl) {
                // open web browser
                String url = "http://localhost:" + port + "/hawtio";
                System.setProperty("hawtio.url", url);
                if (openUrl && Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().browse(new URI(url));
                    } catch (Exception e) {
                        System.err.println("Failed to open browser session, to access Hawtio open url: " + url);
                    }
                }
            }

            // keep JVM running
            installHangupInterceptor();
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while launching Hawtio");
            return 1;
        } catch (Exception e) {
            System.err.println("Cannot launch Hawtio due to: " + e.getMessage());
            return 1;
        } finally {
            downloader.stop();
        }

        return 0;
    }

    private ClassLoader createClassLoader() {
        return new DependencyDownloaderClassLoader(null);
    }

    private void installHangupInterceptor() {
        Thread task = new Thread(shutdownLatch::countDown);
        Runtime.getRuntime().addShutdownHook(task);
    }

}
