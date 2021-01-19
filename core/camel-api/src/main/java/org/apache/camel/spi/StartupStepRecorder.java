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
package org.apache.camel.spi;

import org.apache.camel.StartupStep;
import org.apache.camel.StaticService;

/**
 * To record {@link StartupStep} during startup to allow to capture diagnostic information to help troubleshoot Camel
 * applications via various tooling such as Java Flight Recorder.
 */
public interface StartupStepRecorder extends StaticService {

    /**
     * Whether recording is enabled
     */
    boolean isEnabled();

    /**
     * Whether recording is enabled
     */
    void setEnabled(boolean enabled);

    /**
     * Whether to automatic disable this recorder after Camel has been started.
     * This is done by default to remove any overhead after the startup process is done.
     */
    boolean isDisableAfterStarted();

    /**
     * Whether to automatic disable this recorder after Camel has been started.
     * This is done by default to remove any overhead after the startup process is done.
     */
    void setDisableAfterStarted(boolean disableAfterStarted);

    /**
     * To filter our sub steps at a maximum depth
     */
    void setMaxDepth(int level);

    /**
     * To filter our sub steps at a maximum depth
     */
    int getMaxDepth();

    /**
     * Beings a new step.
     *
     * Important must call {@link #endStep(StartupStep)} to end the step.
     *
     * @param type        the source
     * @param name        name of the step
     * @param description description of the step
     */
    StartupStep beginStep(Class<?> type, String name, String description);

    /**
     * Ends the step
     */
    void endStep(StartupStep step);

}
