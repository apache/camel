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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ReferenceIdTest {

    @Test
    public void missingAndEmptyErrorsShouldHashAlike() {
        final ReferenceId withoutErrors = new ReferenceId("ref1", "001", null);
        final ReferenceId withEmptyErrors = new ReferenceId("ref1", "001", Collections.emptyList());

        // equals() compares getErrors(), which turns a null list into an empty one
        assertEquals(withoutErrors, withEmptyErrors);
        assertEquals(withoutErrors.hashCode(), withEmptyErrors.hashCode());
    }

    @Test
    public void equalReferenceIdsShouldHashAlike() {
        final ReferenceId one = new ReferenceId("ref1", "001", null);
        final ReferenceId other = new ReferenceId("ref1", "001", null);

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
    }

    @Test
    public void differentReferenceIdsShouldNotBeEqual() {
        final ReferenceId one = new ReferenceId("ref1", "001", null);

        assertNotEquals(one, new ReferenceId("ref2", "001", null));
        assertNotEquals(one, new ReferenceId("ref1", "002", null));
    }

}
