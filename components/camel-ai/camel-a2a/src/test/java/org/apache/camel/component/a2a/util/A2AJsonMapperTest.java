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
package org.apache.camel.component.a2a.util;

import java.util.Map;

import org.apache.camel.component.a2a.model.Task;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2AJsonMapperTest {

    @Test
    void customizingARebuiltMapperDoesNotChangeTheSharedOne() {
        ObjectMapper custom = A2AJsonMapper.instance().rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        assertThat(custom.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
        assertThat(A2AJsonMapper.instance().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
    }

    @Test
    void convertValueReportsAFailedConversionAsIllegalArgument() {
        assertThatThrownBy(() -> A2AJsonMapper.convertValue(Map.of("history", "not-a-list"), Task.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(JacksonException.class);
    }
}
