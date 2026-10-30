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

/**
 * An integration that a {@link ProjectRunner} started.
 *
 * @since 4.24
 */
public interface RunningIntegration {

    /**
     * The pid of the process of the integration (this JVM with the jbang runtime), or 0 when the run ended before it
     * was started (such as a build that failed).
     */
    long pid();

    /**
     * Waits for the integration to end. With the jbang runtime, the integration runs in the thread that waits for it,
     * as camel run does.
     *
     * @return           the exit code, as camel run exits with it
     * @throws Exception why the integration failed, such as a route that failed to start, with a run in this JVM
     */
    int waitFor() throws Exception;

    /**
     * Stops the integration and the processes it started, and removes the files of the run. They are also removed when
     * this JVM exits. With the jbang runtime, it stops Camel, and the thread that waits for the integration returns.
     */
    void stop();
}
