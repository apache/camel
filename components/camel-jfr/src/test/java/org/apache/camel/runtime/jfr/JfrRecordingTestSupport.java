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

    protected List<RecordedEvent> recordAndRun(Class<?>[] eventClasses, ThrowingRunnable action) throws Exception {
        for (Class<?> c : eventClasses) {
            FlightRecorder.register(c.asSubclass(jdk.jfr.Event.class));
        }
        Path file = Files.createTempFile("camel-runtime-test", ".jfr");
        try (Recording recording = new Recording()) {
            for (Class<?> c : eventClasses) {
                recording.enable(c.getName());
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
            for (Class<?> c : eventClasses) {
                FlightRecorder.unregister(c.asSubclass(jdk.jfr.Event.class));
            }
            Files.deleteIfExists(file);
        }
    }
}
