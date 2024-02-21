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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.StartupStep;

/**
 * {@link org.apache.camel.spi.StartupStepRecorder} that captures each step event.
 */
public class BacklogStartupStepRecorder extends DefaultStartupStepRecorder {

    private final List<StartupStep> steps = new ArrayList<>();

    public BacklogStartupStepRecorder() {
        setEnabled(true);
    }

    @Override
    protected void onEndStep(StartupStep step) {
        steps.add(step);
    }

    @Override
    public Stream<StartupStep> steps() {
        return steps.stream();
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
        steps.clear();
    }

    @Override
    public String toString() {
        return "backlog";
    }
}
