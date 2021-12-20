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

    public FlightRecorderStartupStepRecorder() {
        // should default be enabled if discovered from classpath
        setEnabled(true);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        if (isRecording()) {
            FlightRecorder.register(FlightRecorderStartupStep.class);
            Configuration config = Configuration.getConfiguration(getRecordingProfile());
            rec = new Recording(config);
            rec.setName("Camel Recording");

            if (!"false".equals(getRecordingDir())) {
                // recording to disk can be turned off by setting to false
                Path dir = getRecordingDir() != null ? Paths.get(getRecordingDir()) : Paths.get(".");
                Path file = Files.createTempFile(dir, "camel-recording", ".jfr");
                // when stopping then the recording is automatic dumped by flight recorder
                rec.setDestination(file);
            }

            if (getStartupRecorderDuration() == 0) {
                if (rec.getDestination() != null) {
                    rec.setDumpOnExit(true);
                    LOG.info("Java flight recorder with profile: {} will be saved to file on JVM exit: {}",
                            getRecordingProfile(), rec.getDestination());
                }
            } else if (getStartupRecorderDuration() > 0) {
                rec.setDuration(Duration.ofSeconds(getStartupRecorderDuration()));
                LOG.info("Starting Java flight recorder with profile: {} and duration: {} seconds", getRecordingProfile(),
                        getStartupRecorderDuration());

                // add listener to trigger auto-save when duration is hit
                frl = new FlightRecorderListener() {
                    @Override
                    public void recordingStateChanged(Recording recording) {
                        if (recording == rec && recording.getState().equals(RecordingState.STOPPED)) {
                            LOG.info("Java flight recorder stopped after {} seconds and saved to file: {}",
                                    getStartupRecorderDuration(), rec.getDestination());
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

        if (rec != null) {
            // if < 0 then manual stop the recording
            if (getStartupRecorderDuration() < 0) {
                LOG.debug("Stopping Java flight recorder");
                // do GC before stopping to force flushing data into the recording
                System.gc();
                rec.stop();
                LOG.info("Java flight recorder stopped and saved to file: {}", rec.getDestination());
            }
            FlightRecorder.unregister(FlightRecorderStartupStep.class);
            if (frl != null) {
                FlightRecorder.removeListener(frl);
            }
            rec = null;
            frl = null;
        }
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
