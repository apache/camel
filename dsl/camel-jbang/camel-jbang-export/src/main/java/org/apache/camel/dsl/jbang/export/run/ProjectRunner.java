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
package org.apache.camel.dsl.jbang.export.run;

import org.apache.camel.dsl.jbang.export.ProjectExporter;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;

/**
 * Runs Camel files, the Java API of camel run. A run is an export with a run on top: with Camel Main, the files are
 * exported to a Maven project in a directory of the run, which is built with Maven and started in a JVM of its own; an
 * existing Maven project (a pom.xml as the file to run) is run with Maven as it is.
 *
 * <pre>
 * RunRequest request = new RunRequest();
 * request.files.add("hello.camel.yaml");
 * request.runtime = RuntimeType.main;
 * RunningIntegration integration = new ProjectRunner().start(request);
 * int exit = integration.waitFor();
 * </pre>
 *
 * Only the Camel Main runtime is run so far; camel run runs the others itself.
 *
 * @since 4.24
 */
public class ProjectRunner extends ProjectExporter {

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
     * Starts the integration: exports and builds the project (the output of Maven goes to the console of this JVM), and
     * starts the application.
     *
     * @param  request the request, which is not changed
     * @return         the running integration, or a run that ended with the exit code of the failure (such as a build
     *                 that failed)
     */
    public RunningIntegration start(RunRequest request) throws Exception {
        if (request.runtime == RuntimeType.main) {
            return new CamelMainRunner(this, request).start();
        }
        throw new IllegalArgumentException("Cannot run the runtime " + request.runtime + " with ProjectRunner");
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
