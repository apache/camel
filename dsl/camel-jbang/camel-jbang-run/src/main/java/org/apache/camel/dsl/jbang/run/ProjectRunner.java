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
package org.apache.camel.dsl.jbang.run;

import java.nio.file.Path;

import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.export.ProjectExporter;

/**
 * Runs Camel files, the Java API of camel run. With the jbang runtime, the files run in this JVM. A run with Camel
 * Main, Quarkus or Spring Boot is an export with a run on top: the files are exported to a Maven project in a directory
 * of the run, which is built and started with Maven (Camel Main in a JVM of its own, Quarkus with quarkus:run or
 * quarkus:dev, Spring Boot with spring-boot:run); an existing Maven project (a pom.xml as the file to run) is run with
 * Maven as it is.
 *
 * <pre>
 * RunRequest request = new RunRequest();
 * request.files.add("hello.camel.yaml");
 * RunningIntegration integration = new ProjectRunner().start(request);
 * int exit = integration.waitFor();
 * </pre>
 *
 * What only the command line does is not part of a run: configuring the logging, and running in another JVM (in the
 * background, or with another Camel version).
 *
 * @since 4.24
 */
public class ProjectRunner extends ProjectExporter {

    /**
     * The directory with the projects that are exported to be run, one directory per run.
     */
    public static final String RUN_PLATFORM_DIR = ".camel-jbang-run";

    public ProjectRunner() {
        this(new RunContext());
    }

    public ProjectRunner(RunContext context) {
        super(context);
    }

    @Override
    public RunContext getContext() {
        return (RunContext) super.getContext();
    }

    /**
     * Starts the integration. With the jbang runtime, Camel starts in this JVM. With Camel Main, Quarkus and Spring
     * Boot, the project is exported and built (the output of Maven goes to the console of this JVM), and the
     * application is started; with Quarkus and Spring Boot, and an existing project, the process of the integration is
     * Maven, which runs the application, and stopping it stops the application too.
     *
     * @param  request the request, which is completed as camel run does: a directory as the file to run (camel run .),
     *                 the runtime of an existing Maven project, and with the jbang runtime what the run finds (such as
     *                 the name, the files and the application properties)
     * @return         the running integration, or a run that ended with the exit code of the failure (such as a build
     *                 that failed)
     */
    public RunningIntegration start(RunRequest request) throws Exception {
        // camel run . or camel run dirName, and the runtime of an existing Maven project, which cannot run in this JVM
        Path dir = RunPipeline.detectDirectory(request);
        RunPipeline.detectRuntime(request);
        if (request.runtime == RuntimeType.main) {
            return new CamelMainRunner(this, request).start();
        } else if (request.runtime == RuntimeType.quarkus) {
            return new QuarkusRunner(this, request).start();
        } else if (request.runtime == RuntimeType.springBoot) {
            return new SpringBootRunner(this, request).start();
        } else if (request.runtime == RuntimeType.jbang) {
            // the directory has the application properties of a run in this JVM (a run with Maven exports its files)
            if (dir != null) {
                request.baseDir = dir;
            }
            return configure(request).start();
        }
        throw new IllegalArgumentException("Cannot run the runtime " + request.runtime + " with ProjectRunner");
    }

    /**
     * Configures a run with the jbang runtime, which runs in this JVM, without starting it: the work directory, the
     * application properties, the files to run, and the Camel Main of the run. camel run configures the run, and
     * decides only then whether it starts it ({@link ConfiguredRun#start()}), or runs the integration in another JVM
     * (in the background, or with another Camel version). {@link #start(RunRequest)} configures and starts it.
     *
     * @param  request the request with the jbang runtime, which the configuration completes (such as the name, the
     *                 files and the application properties)
     * @return         the configured run, or a run that ended (no files to run, or a file that does not exist)
     */
    public ConfiguredRun configure(RunRequest request) throws Exception {
        // camel run . or camel run dirName, for a caller that configures the run itself (as camel run does); after
        // start(RunRequest) the request has no directory as the file to run any more, so this finds nothing
        Path dir = RunPipeline.detectDirectory(request);
        if (dir != null) {
            request.baseDir = dir;
        }
        if (request.runtime != RuntimeType.jbang) {
            throw new IllegalArgumentException("Only the jbang runtime runs in this JVM, not " + request.runtime);
        }
        return new JBangRunner(this, request).configure();
    }

    /**
     * Starts the integration and waits for it to end, as camel run does.
     *
     * @return the exit code
     */
    public int run(RunRequest request) throws Exception {
        return start(request).waitFor();
    }
}
