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
package org.apache.camel.support.startup;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.StartupStep;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Default {@link StartupStepRecorder} that is always disabled.
 */
public class DefaultStartupStepRecorder extends ServiceSupport implements StartupStepRecorder {

    private static final StartupStep DISABLED_STEP = new StartupStep() {
        @Override
        public String getType() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public int getId() {
            return 0;
        }

        @Override
        public int getParentId() {
            return 0;
        }

        @Override
        public int getLevel() {
            return 0;
        }

        @Override
        public long getBeginTime() {
            return 0;
        }

        @Override
        public long getDuration() {
            return 0;
        }

        @Override
        public void endStep() {
            // noop
        }

    };

    private final AtomicInteger stepCounter = new AtomicInteger();
    private final Deque<Integer> currentSteps = new ArrayDeque<>();

    private boolean enabled;
    private int maxDepth = -1;
    private long startupRecorderDuration;
    private boolean recording;
    private String recordingDir;
    private String recordingProfile = "default";

    public DefaultStartupStepRecorder() {
        currentSteps.offerFirst(0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public long getStartupRecorderDuration() {
        return startupRecorderDuration;
    }

    @Override
    public void setStartupRecorderDuration(long startupRecorderDuration) {
        this.startupRecorderDuration = startupRecorderDuration;
    }

    @Override
    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public boolean isRecording() {
        return recording;
    }

    @Override
    public void setRecording(boolean recording) {
        this.recording = recording;
    }

    @Override
    public String getRecordingDir() {
        return recordingDir;
    }

    @Override
    public void setRecordingDir(String recordingDir) {
        this.recordingDir = recordingDir;
    }

    @Override
    public String getRecordingProfile() {
        return recordingProfile;
    }

    @Override
    public void setRecordingProfile(String recordingProfile) {
        this.recordingProfile = recordingProfile;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    public void doStop() throws Exception {
        enabled = false;
        currentSteps.clear();
    }

    public StartupStep beginStep(Class<?> type, String name, String description) {
        if (enabled) {
            int level = currentSteps.size() - 1;
            if (maxDepth != -1 && level >= maxDepth) {
                return DISABLED_STEP;
            }
            int id = stepCounter.incrementAndGet();
            Integer parent = currentSteps.peekFirst();
            int pid = parent != null ? parent : 0;
            StartupStep step = createStartupStep(type.getSimpleName(), name, description, id, pid, level);
            onBeginStep(step);
            currentSteps.offerFirst(id);
            return step;
        } else {
            return DISABLED_STEP;
        }
    }

    public void endStep(StartupStep step) {
        if (step != DISABLED_STEP) {
            currentSteps.pollFirst();
            step.endStep();
            onEndStep(step);
        }
    }

    public StartupStep createStartupStep(String type, String name, String description, int id, int parentId, int level) {
        return new DefaultStartupStep(type, name, description, id, parentId, level, System.currentTimeMillis());
    }

    protected void onBeginStep(StartupStep step) {
        // noop
    }

    protected void onEndStep(StartupStep step) {
        // noop
    }

}
