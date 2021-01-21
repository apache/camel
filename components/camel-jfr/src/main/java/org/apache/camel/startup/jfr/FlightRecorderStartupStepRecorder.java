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
package org.apache.camel.startup.jfr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import org.apache.camel.StartupStep;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.startup.DefaultStartupStepRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To capture startup steps to be emitted to Java Flight Recorder.
 */
@JdkService(StartupStepRecorder.FACTORY)
public class FlightRecorderStartupStepRecorder extends DefaultStartupStepRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(FlightRecorderStartupStepRecorder.class);

    private Recording rec;
    private FlightRecorderListener frl;

    @Override
    public void doStart() throws Exception {
        super.doStart();

        if (isRecording()) {
            FlightRecorder.register(FlightRecorderStartupStep.class);
            Configuration config = Configuration.getConfiguration(getRecordingProfile());
            rec = new Recording(config);
            rec.setName("Camel Recording");
            if (getStartupRecorderDuration() == 0) {
                Path dir = getRecordingDir() != null ? Paths.get(getRecordingDir()) : Paths.get(System.getenv().get("HOME"));
                Path file = Files.createTempFile(dir, "camel-recording", ".jfr");
                rec.setDumpOnExit(true);
                rec.setDestination(file);
                LOG.info("Java flight recorder will be saved to file on JVM exit: {}", file);
            }

            if (getStartupRecorderDuration() > 0) {
                rec.setDuration(Duration.ofSeconds(getStartupRecorderDuration()));
                LOG.info("Starting Java flight recorder with profile: {} and duration: {} seconds", getRecordingProfile(),
                        getStartupRecorderDuration());

                // add listener to trigger auto-save when duration is hit
                frl = new FlightRecorderListener() {
                    @Override
                    public void recordingStateChanged(Recording recording) {
                        if (recording == rec && recording.getState().equals(RecordingState.STOPPED)) {
                            LOG.info("Stopping Java flight recorder after {} seconds elapsed", getStartupRecorderDuration());
                            dumpRecording();
                        }
                    }
                };
                FlightRecorder.addListener(frl);
            } else {
                LOG.info("Starting Java flight recorder with profile: {}", getRecordingProfile());
            }
            rec.start();
        }
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        if (rec != null && getStartupRecorderDuration() != 0) {
            dumpRecording();
        }
    }

    protected void dumpRecording() {
        if (!"false".equals(getRecordingDir())) {
            try {
                Path dir = getRecordingDir() != null ? Paths.get(getRecordingDir()) : Paths.get(System.getenv().get("HOME"));
                Path file = Files.createTempFile(dir, "camel-recording-", ".jfr");
                if (rec.getState().equals(RecordingState.RUNNING)) {
                    // need to do GC to capture details to the recording (specially when its short running)
                    LOG.info("Stopping Java flight recorder");
                    System.gc();
                    rec.stop();
                }
                if (rec.getState().equals(RecordingState.STOPPED)) {
                    rec.dump(file);
                    LOG.info("Java flight recorder saved to file: {}", file);
                }
            } catch (Exception e) {
                LOG.warn("Error saving Java flight recorder recording to file", e);
            }
        }
        FlightRecorder.unregister(FlightRecorderStartupStep.class);
        if (frl != null) {
            FlightRecorder.removeListener(frl);
        }

        rec = null;
        frl = null;
    }

    public FlightRecorderStartupStepRecorder() {
        setEnabled(true);
        // pre-empty enable recording so we have as early as possible recording started
        setRecording(true);
    }

    @Override
    public StartupStep createStartupStep(String type, String name, String description, int id, int parentId, int level) {
        return new FlightRecorderStartupStep(name, id, parentId, level, type, description);
    }

    @Override
    public String toString() {
        return "java-flight-recorder";
    }
}
