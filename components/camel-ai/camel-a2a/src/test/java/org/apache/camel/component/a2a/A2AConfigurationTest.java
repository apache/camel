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
package org.apache.camel.component.a2a;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2AConfigurationTest {

    @Test
    void validateAcceptsDefaultConfiguration() {
        A2AConfiguration configuration = new A2AConfiguration();

        assertThatCode(configuration::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsZeroBackoffWhenPushRetriesAreEnabled() {
        A2AConfiguration configuration = new A2AConfiguration();
        configuration.setPushRetryAttempts(1);
        configuration.setPushRetryBackoffMs(0);

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pushRetryBackoffMs");
    }

    @Test
    void validateRejectsInvalidTimingAndCapacityOptions() {
        A2AConfiguration configuration = new A2AConfiguration();
        configuration.setSseHeartbeatInterval(0);

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sseHeartbeatInterval");
    }
}
