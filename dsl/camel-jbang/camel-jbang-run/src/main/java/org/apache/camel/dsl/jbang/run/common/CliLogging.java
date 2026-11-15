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
package org.apache.camel.dsl.jbang.run.common;

/**
 * Installs the SLF4J provider of the CLI ({@link CliLoggerProvider}). A class of its own without SLF4J types, so that a
 * command that never logs, such as camel version, loads no class of SLF4J.
 */
public final class CliLogging {

    // the system properties of SLF4J for the provider, and for what it reports about itself
    static final String PROVIDER_PROPERTY = "slf4j.provider";
    static final String VERBOSITY_PROPERTY = "slf4j.internal.verbosity";

    // the name of CliLoggerProvider, without loading it
    static final String PROVIDER = "org.apache.camel.dsl.jbang.run.common.CliLoggerProvider";

    // whether the CLI configured the logging, and what the provider does then once SLF4J chose it
    private static volatile boolean configured;
    private static Runnable onConfigured;

    private CliLogging() {
    }

    /**
     * Makes SLF4J use the provider of the CLI, unless another one is asked for. Call it first thing in the main method
     * of the CLI, before anything creates a logger.
     */
    public static void install() {
        if (System.getProperty(PROVIDER_PROPERTY) == null) {
            System.setProperty(PROVIDER_PROPERTY, PROVIDER);
            if (System.getProperty(VERBOSITY_PROPERTY) == null) {
                // SLF4J tells on stderr which provider it loads, when it is given by the system property
                System.setProperty(VERBOSITY_PROPERTY, "WARN");
            }
        }
    }

    /**
     * Whether the loggers of the CLI log nothing yet, as the CLI did not configure the logging: then nothing logged to
     * log4j, and log4j is not started.
     */
    static boolean isDeferred() {
        return !configured && PROVIDER.equals(System.getProperty(PROVIDER_PROPERTY));
    }

    /**
     * The CLI configured log4j: from now on the loggers of the CLI log with it. The CLI calls it when it configures
     * log4j, see {@link RuntimeUtil#configureLog}.
     */
    public static void loggingConfigured() {
        Runnable task;
        synchronized (CliLogging.class) {
            configured = true;
            task = onConfigured;
        }
        if (task != null) {
            task.run();
        }
    }

    // for the tests: as in a new JVM
    static synchronized void reset() {
        configured = false;
        onConfigured = null;
    }

    // SLF4J chose the provider of the CLI
    static void bound(Runnable task) {
        boolean now;
        synchronized (CliLogging.class) {
            onConfigured = task;
            now = configured;
        }
        if (now) {
            task.run();
        }
    }
}
