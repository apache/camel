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

package org.apache.camel.component.langchain4j.agent.pojos;

import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Test input guardrail that tracks call count. This guardrail always succeeds but provides call tracking for
 * verification.
 */
public class TestFailingInputGuardrail implements InputGuardrail {

    private static final AtomicInteger callCount = new AtomicInteger(0);
    private static volatile boolean wasValidated = false;

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        wasValidated = true;
        callCount.incrementAndGet();

        // Always succeed - focus on testing the integration mechanism and call tracking
        return InputGuardrailResult.success();
    }

    /**
     * Resets all tracking state. Should be called before each test.
     */
    public static void reset() {
        wasValidated = false;
        callCount.set(0);
    }

    /**
     * @return the number of times this guardrail was called
     */
    public static int getCallCount() {
        return callCount.get();
    }

    /**
     * @return true if this guardrail was validated at least once
     */
    public static boolean wasValidated() {
        return wasValidated;
    }
}
