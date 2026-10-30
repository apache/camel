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

/**
 * An integration that a {@link ProjectRunner} started.
 *
 * @since 4.24
 */
public interface RunningIntegration {

    /**
     * The pid of the process of the integration, or 0 when the run ended before it was started (such as a build that
     * failed).
     */
    long pid();

    /**
     * Waits for the integration to end.
     *
     * @return the exit code, as camel run exits with it
     */
    int waitFor() throws InterruptedException;

    /**
     * Stops the integration and the processes it started, and removes the files of the run. They are also removed when
     * this JVM exits.
     */
    void stop();
}
