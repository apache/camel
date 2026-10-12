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
package org.apache.camel.service.lra;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPENSATE;
import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPLETE;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPENSATION_KEY;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPLETION_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LRACallbackSignerTest {

    private final LRACallbackSigner signer = new LRACallbackSigner("a-callback-secret");

    @Test
    void signatureMatchesTheSignedParameters() {
        String signature = signer.sign(PARTICIPANT_PATH_COMPENSATE, parameters());

        assertTrue(signer.verify(PARTICIPANT_PATH_COMPENSATE, parameters(), signature));
    }

    @Test
    void signatureDoesNotDependOnTheParameterOrder() {
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("id", "42");
        ordered.put("name", "Nicola");
        Map<String, String> reversed = new LinkedHashMap<>();
        reversed.put("name", "Nicola");
        reversed.put("id", "42");

        assertEquals(signer.sign(PARTICIPANT_PATH_COMPENSATE, ordered), signer.sign(PARTICIPANT_PATH_COMPENSATE, reversed));
    }

    @Test
    void changedParameterValueDoesNotMatch() {
        String signature = signer.sign(PARTICIPANT_PATH_COMPENSATE, parameters());
        Map<String, String> changed = parameters();
        changed.put("id", "43");

        assertFalse(signer.verify(PARTICIPANT_PATH_COMPENSATE, changed, signature));
    }

    @Test
    void addedParameterDoesNotMatch() {
        String signature = signer.sign(PARTICIPANT_PATH_COMPENSATE, parameters());
        Map<String, String> added = parameters();
        added.put("extra", "value");

        assertFalse(signer.verify(PARTICIPANT_PATH_COMPENSATE, added, signature));
    }

    @Test
    void removedParameterDoesNotMatch() {
        String signature = signer.sign(PARTICIPANT_PATH_COMPENSATE, parameters());
        Map<String, String> removed = parameters();
        removed.remove("name");

        assertFalse(signer.verify(PARTICIPANT_PATH_COMPENSATE, removed, signature));
    }

    @Test
    void signatureIsBoundToTheCallbackPath() {
        String signature = signer.sign(PARTICIPANT_PATH_COMPENSATE, parameters());

        assertFalse(signer.verify(PARTICIPANT_PATH_COMPLETE, parameters(), signature));
    }

    @Test
    void signatureIsBoundToTheSecret() {
        String signature = new LRACallbackSigner("another-secret").sign(PARTICIPANT_PATH_COMPENSATE, parameters());

        assertFalse(signer.verify(PARTICIPANT_PATH_COMPENSATE, parameters(), signature));
    }

    @Test
    void missingSignatureDoesNotMatch() {
        assertFalse(signer.verify(PARTICIPANT_PATH_COMPENSATE, parameters(), null));
    }

    @Test
    void nullValueIsSignedAsAnEmptyValue() {
        Map<String, String> withNull = parameters();
        withNull.put("note", null);
        Map<String, String> withEmpty = parameters();
        withEmpty.put("note", "");

        assertEquals(signer.sign(PARTICIPANT_PATH_COMPENSATE, withNull), signer.sign(PARTICIPANT_PATH_COMPENSATE, withEmpty));
    }

    @Test
    void separatorsInValuesDoNotChangeTheParameterBoundaries() {
        Map<String, String> one = new HashMap<>();
        one.put("a", "b&c=d");
        Map<String, String> two = new HashMap<>();
        two.put("a", "b");
        two.put("c", "d");

        assertNotEquals(signer.sign(PARTICIPANT_PATH_COMPENSATE, one), signer.sign(PARTICIPANT_PATH_COMPENSATE, two));
    }

    private static Map<String, String> parameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(URL_COMPENSATION_KEY, "direct://cancelOrder");
        parameters.put(URL_COMPLETION_KEY, "direct://confirmOrder");
        parameters.put("id", "42");
        parameters.put("name", "Nicola");
        return parameters;
    }
}
