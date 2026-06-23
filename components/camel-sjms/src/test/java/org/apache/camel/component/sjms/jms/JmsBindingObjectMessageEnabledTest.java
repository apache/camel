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
package org.apache.camel.component.sjms.jms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-style tests for the {@code objectMessageEnabled} guards on {@link JmsBinding}. Complements the broker-based
 * {@code SjmsObjectMessageEnabledTest} by verifying each guard in isolation, including the {@code transferException}
 * reply path which is otherwise difficult to assert end-to-end (a broken reply path manifests as a producer-side
 * timeout, hiding the underlying guard message).
 */
public class JmsBindingObjectMessageEnabledTest {

    private JmsBinding binding(boolean objectMessageEnabled) {
        return new JmsBinding(true, true, null, null, null, null, null, objectMessageEnabled);
    }

    @Test
    public void testTransferExceptionReplyRefusedByDefault() {
        // Direct invocation of the public makeJmsMessage(Exception, ...) path. The guard fires before the Session is
        // touched, so passing null is safe and isolates the assertion.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> binding(false).makeJmsMessage(null, null, null, new RuntimeException("boom")));
        assertTrue(ex.getMessage().contains("transferException reply"),
                "Expected transferException-specific operation in error message, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("objectMessageEnabled=true"),
                "Expected guidance to enable objectMessageEnabled=true, got: " + ex.getMessage());
    }
}
