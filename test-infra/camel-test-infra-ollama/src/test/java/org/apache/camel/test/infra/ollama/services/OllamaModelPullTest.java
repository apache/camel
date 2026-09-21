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
package org.apache.camel.test.infra.ollama.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaModelPullTest {

    @Test
    void aPullThatSucceededIsAccepted() {
        assertDoesNotThrow(() -> OllamaLocalContainerInfraService.requireSuccessfulPull("granite4:3b", 0, ""));
    }

    @Test
    void aPullThatFailedNamesTheModelAndTheError() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> OllamaLocalContainerInfraService.requireSuccessfulPull(
                        "qwen2.5:0.5b", 1, "pull model manifest: file does not exist\n"));

        assertTrue(e.getMessage().contains("qwen2.5:0.5b"), e.getMessage());
        assertTrue(e.getMessage().contains("exit code 1"), e.getMessage());
        assertTrue(e.getMessage().contains("pull model manifest: file does not exist"), e.getMessage());
    }

    @Test
    void aPullThatFailedSilentlyStillFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> OllamaLocalContainerInfraService.requireSuccessfulPull("granite-embedding:30m", 125, null));

        assertTrue(e.getMessage().contains("no error output"), e.getMessage());
    }
}
