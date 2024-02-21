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

import java.util.stream.Stream;

import org.apache.camel.StartupStep;
import org.apache.camel.StaticService;

/**
 * To record {@link StartupStep} during startup to allow to capture diagnostic information to help troubleshoot Camel
 * applications via various tooling such as Java Flight Recorder.
 */
public interface StartupStepRecorder extends StaticService {

    /**
     * Service factory key.
     */
    String FACTORY = "startup-step-recorder";

    /**
     * Whether recording is enabled
     */
    boolean isEnabled();

    /**
     * Whether recording is enabled
     */
    void setEnabled(boolean enabled);

    /**
     * How long time to run the startup recorder.
     *
     * Use 0 (default) to stop the recorder after Camel has been started. Use -1 to keep the recorder running until
     * Camel is being stopped. A positive value is to run the recorder for N seconds.
     */
    long getStartupRecorderDuration();

    /**
     * How long time to run the startup recorder.
     *
     * Use 0 (default) to stop the recorder after Camel has been started. Use -1 to keep the recorder running until
     * Camel is being stopped. A positive value is to run the recorder for N seconds.
     */
    void setStartupRecorderDuration(long startupRecorderDuration);

    String getRecordingDir();

    /**
     * Directory to store the recording. By default the current directory will be used.
     */
    void setRecordingDir(String recordingDir);

    String getRecordingProfile();

    /**
     * To use a specific Java Flight Recorder profile configuration, such as default or profile.
     *
     * The default is default.
     */
    void setRecordingProfile(String profile);

    /**
     * To filter our sub steps at a maximum depth
     */
    void setMaxDepth(int level);

    /**
     * To filter our sub steps at a maximum depth
     */
    int getMaxDepth();

    /**
     * Whether to start flight recorder recording. This is only in use if camel-jfr is being used.
     */
    void setRecording(boolean recording);

    /**
     * Whether to start flight recorder recording. This is only in use if camel-jfr is being used.
     */
    boolean isRecording();

    /**
     * Beings a new step.
     * <p>
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

    /**
     * Some records will capture all steps which can be accessed on demand.
     */
    default Stream<StartupStep> steps() {
        return Stream.empty();
    }

}
