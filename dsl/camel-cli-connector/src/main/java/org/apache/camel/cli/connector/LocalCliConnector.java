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
package org.apache.camel.cli.connector;

import java.io.File;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.console.DevConsole;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI Connector for local management of Camel integrations from the Camel CLI.
 */
public class LocalCliConnector extends ServiceSupport implements CliConnector, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(LocalCliConnector.class);

    private CamelContext camelContext;
    private int delay = 2000;
    private String platform;
    private String platformVersion;
    private String mainClass;
    private final AtomicBoolean terminating = new AtomicBoolean();
    private ScheduledExecutorService executor;
    private File lockFile;
    private File statusFile;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        terminating.set(false);

        // what platform are we running
        CliConnectorFactory ccf = camelContext.adapt(ExtendedCamelContext.class).getCliConnectorFactory();
        mainClass = ccf.getRuntimeStartClass();
        if (mainClass == null) {
            mainClass = camelContext.getGlobalOption("CamelMainClass");
        }
        platform = ccf.getRuntime();
        if (platform == null) {
            // use camel context name to guess platform if not specified
            String sn = camelContext.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (sn.contains("boot")) {
                platform = "Spring Boot";
            } else if (sn.contains("spring")) {
                platform = "Spring";
            } else if (sn.contains("quarkus")) {
                platform = "Quarkus";
            } else if (sn.contains("osgi")) {
                platform = "Karaf";
            } else if (sn.contains("cdi")) {
                platform = "CDI";
            } else if (camelContext.getName().equals("CamelJBang")) {
                platform = "JBang";
            } else {
                platform = "Camel";
            }
        }
        platformVersion = ccf.getRuntimeVersion();

        // create thread from JDK so it is not managed by Camel because we want the pool to be independent when
        // camel is being stopped which otherwise can lead to stopping the thread pool while the task is running
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            String threadName = ThreadHelper.resolveThreadName(null, "LocalCliConnector");
            return new Thread(r, threadName);
        });

        lockFile = createLockFile(getPid());
        if (lockFile != null) {
            statusFile = createLockFile(lockFile.getName() + "-status.json");
            executor.scheduleWithFixedDelay(this::statusTask, 0, delay, TimeUnit.MILLISECONDS);
            LOG.info("Local CLI Connector started");
        } else {
            LOG.warn("Cannot create PID file: {}. This integration cannot be managed by Camel CLI.", getPid());
        }
    }

    @Override
    public void sigterm() {
        // we are terminating
        terminating.set(true);

        try {
            camelContext.stop();
        } finally {
            if (lockFile != null) {
                FileUtil.deleteFile(lockFile);
            }
            if (statusFile != null) {
                FileUtil.deleteFile(statusFile);
            }
            ServiceHelper.stopAndShutdownService(this);
        }
    }

    protected void statusTask() {
        if (terminating.get()) {
            return; // terminating in progress
        }
        if (!lockFile.exists()) {
            // if the lock file is deleted then stop
            sigterm();
            return;
        }
        try {
            JsonObject root = new JsonObject();

            // what runtime are in use
            JsonObject rc = new JsonObject();
            String dir = new File(".").getAbsolutePath();
            dir = FileUtil.onlyPath(dir);
            rc.put("pid", ProcessHandle.current().pid());
            rc.put("directory", dir);
            ProcessHandle.current().info().user().ifPresent(u -> rc.put("user", u));
            rc.put("platform", platform);
            if (platformVersion != null) {
                rc.put("version", platformVersion);
            }
            if (mainClass != null) {
                rc.put("mainClass", mainClass);
            }
            root.put("runtime", rc);

            // collect details via console
            DevConsole dc = camelContext.adapt(ExtendedCamelContext.class)
                    .getDevConsoleResolver().resolveDevConsole("context");
            DevConsole dc2 = camelContext.adapt(ExtendedCamelContext.class)
                    .getDevConsoleResolver().resolveDevConsole("route");
            int ready = 0;
            int total = 0;
            if (dc != null && dc2 != null) {
                JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON);
                JsonObject json2 = (JsonObject) dc2.call(DevConsole.MediaType.JSON);
                if (json != null && json2 != null) {
                    root.put("context", json);
                    root.put("routes", json2.get("routes"));
                }
            }
            // and health-check readiness
            Collection<HealthCheck.Result> res = HealthCheckHelper.invokeReadiness(camelContext);
            for (var r : res) {
                if (r.getState().equals(HealthCheck.State.UP)) {
                    ready++;
                }
                total++;
            }
            JsonObject hc = new JsonObject();
            hc.put("ready", ready);
            hc.put("total", total);
            root.put("healthChecks", hc);

            LOG.trace("Updating status file: {}", statusFile);
            IOHelper.writeText(root.toJson(), statusFile);
        } catch (Throwable e) {
            // ignore
        }
    }

    @Override
    protected void doStop() throws Exception {
        // cleanup
        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
    }

    private static String getPid() {
        try {
            return "" + ProcessHandle.current().pid();
        } catch (Throwable e) {
            return null;
        }
    }

    private static File createLockFile(String name) {
        File answer = null;
        if (name != null) {
            File dir = new File(System.getProperty("user.home"), ".camel");
            try {
                dir.mkdirs();
                answer = new File(dir, name);
                if (!answer.exists()) {
                    answer.createNewFile();
                }
                answer.deleteOnExit();
            } catch (Exception e) {
                answer = null;
            }
        }
        return answer;
    }

}
