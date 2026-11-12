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
package org.apache.camel.component.opa;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpaBatchOutcomeTest {

    @Test
    void describesTheSanitizedDecisionAndFailureState() {
        assertThat(OpaBatchOutcome.decision(false).toString())
                .isEqualTo("OpaBatchOutcome[allowed=false, decision=false, failureCategory=NONE, failedOpen=false]");
        assertThat(OpaBatchOutcome.failure(OpaBatchOutcome.FailureCategory.UNAVAILABLE, true).toString())
                .isEqualTo("OpaBatchOutcome[allowed=true, decision=null, failureCategory=UNAVAILABLE, failedOpen=true]");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesTheBatchHeaderListThroughJavaSerialization() throws Exception {
        List<OpaBatchOutcome> header = List.copyOf(List.of(
                OpaBatchOutcome.decision(true),
                OpaBatchOutcome.decision(false),
                OpaBatchOutcome.failure(OpaBatchOutcome.FailureCategory.UNAVAILABLE, true),
                OpaBatchOutcome.failure(OpaBatchOutcome.FailureCategory.EVALUATION_ERROR, false)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(header);
        }

        List<OpaBatchOutcome> restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (List<OpaBatchOutcome>) input.readObject();
        }

        assertThat(restored).extracting(OpaBatchOutcome::isAllowed).containsExactly(true, false, true, false);
        assertThat(restored).extracting(OpaBatchOutcome::getDecision).containsExactly(true, false, null, null);
        assertThat(restored).extracting(OpaBatchOutcome::getFailureCategory).containsExactly(
                OpaBatchOutcome.FailureCategory.NONE, OpaBatchOutcome.FailureCategory.NONE,
                OpaBatchOutcome.FailureCategory.UNAVAILABLE, OpaBatchOutcome.FailureCategory.EVALUATION_ERROR);
        assertThat(restored).extracting(OpaBatchOutcome::isFailedOpen).containsExactly(false, false, true, false);
    }
}
