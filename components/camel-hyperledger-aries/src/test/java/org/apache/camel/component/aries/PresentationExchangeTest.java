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
package org.apache.camel.component.aries;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hyperledger.aries.api.present_proof.PresentationExchangeState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.PRESENTATIONS_SENT;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.PRESENTATION_ACKED;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.PRESENTATION_RECEIVED;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.PROPOSAL_RECEIVED;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.PROPOSAL_SENT;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.REQUEST_RECEIVED;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.REQUEST_SENT;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.VERIFIED;

public class PresentationExchangeTest extends AbstractCamelAriesTest {

    @Test
    public void testWorkflow() throws Exception {

        List<PresentationExchangeState> states = Arrays.asList(
                PROPOSAL_SENT,
                PROPOSAL_RECEIVED,
                REQUEST_SENT,
                REQUEST_RECEIVED,
                PRESENTATIONS_SENT,
                PRESENTATION_RECEIVED,
                VERIFIED,
                PRESENTATION_ACKED);

        PresentationExchangeState first = states.stream()
                .peek(ps -> log.info("{}", ps))
                .sorted(Collections.reverseOrder((a, b) -> a.ordinal() - b.ordinal()))
                .findFirst().get();

        Assertions.assertEquals(PRESENTATION_ACKED, first);
    }
}
