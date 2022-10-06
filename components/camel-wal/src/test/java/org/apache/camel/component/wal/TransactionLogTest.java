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

package org.apache.camel.component.wal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransactionLogTest {
    @DisplayName("Tests that can update records on the same layer")
    @Test
    void testCanUpdateSameLayer() {
        assertTrue(TransactionLog.canUpdate(0, 10, 0, 1));
        assertTrue(TransactionLog.canUpdate(0, 10, 0, 10));
        assertFalse(TransactionLog.canUpdate(0, 10, 0, 11));
    }

    @DisplayName("Tests that prevent updating a record that has been rolled-over")
    @Test
    void testCannotUpdateRolledOverRecord() {
        assertFalse(TransactionLog.canUpdate(1, 3, 0, 1));
        assertFalse(TransactionLog.canUpdate(1, 3, 0, 3));
    }

    @DisplayName("Tests that can update records after a roll-over has started")
    @Test
    void testCanUpdateDifferentLayerWithRollOver() {
        assertTrue(TransactionLog.canUpdate(1, 10, 0, 11));
        assertTrue(TransactionLog.canUpdate(1, 10, 1, 10));
        assertFalse(TransactionLog.canUpdate(1, 10, 0, 10));
    }
}
