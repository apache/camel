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
package org.apache.camel.runtime.jfr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

abstract class JfrRecordingTestSupport {

    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs the action with a recording capturing every camel-jfr runtime event, and returns what was recorded.
     */
    protected List<RecordedEvent> recordAndRun(ThrowingRunnable action) throws Exception {
        for (CamelJfrEvents event : CamelJfrEvents.values()) {
            FlightRecorder.register(event.getEventClass());
        }
        Path file = Files.createTempFile("camel-runtime-test", ".jfr");
        try (Recording recording = new Recording()) {
            for (CamelJfrEvents event : CamelJfrEvents.values()) {
                // must enable by class: every event is renamed by @Name, so the Java class name matches nothing
                recording.enable(event.getEventClass());
            }
            recording.start();
            action.run();
            recording.stop();
            recording.dump(file);

            List<RecordedEvent> events = new ArrayList<>();
            try (RecordingFile rf = new RecordingFile(file)) {
                while (rf.hasMoreEvents()) {
                    events.add(rf.readEvent());
                }
            }
            return events;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * @return the recorded events of the given camel-jfr event type
     */
    protected static List<RecordedEvent> eventsOfType(List<RecordedEvent> events, CamelJfrEvents type) {
        return events.stream().filter(e -> type.getEventName().equals(e.getEventType().getName())).toList();
    }
}
