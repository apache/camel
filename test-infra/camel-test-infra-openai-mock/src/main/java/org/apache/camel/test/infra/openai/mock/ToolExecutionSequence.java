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

package org.apache.camel.test.infra.openai.mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a sequence of tool execution steps. Each step can contain multiple parallel tool calls.
 */
public class ToolExecutionSequence {
    private final List<ToolExecutionStep> steps;
    private int currentStepIndex;

    public ToolExecutionSequence() {
        this.steps = new ArrayList<>();
        this.currentStepIndex = 0;
    }

    public void addStep(ToolExecutionStep step) {
        steps.add(step);
    }

    public ToolExecutionStep getCurrentStep() {
        if (hasCurrentStep()) {
            return steps.get(currentStepIndex);
        }
        return new ToolExecutionStep(); // Return empty step if no current step
    }

    public void advanceToNextStep() {
        currentStepIndex++;
    }

    public boolean hasCurrentStep() {
        return currentStepIndex < steps.size();
    }

    public boolean hasMoreSteps() {
        return currentStepIndex < steps.size();
    }

    public boolean isInProgress() {
        return currentStepIndex > 0 && currentStepIndex < steps.size();
    }

    public void reset() {
        currentStepIndex = 0;
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public int getTotalSteps() {
        return steps.size();
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }
}
