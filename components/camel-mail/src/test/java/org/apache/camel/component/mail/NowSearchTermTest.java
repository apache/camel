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
package org.apache.camel.component.mail;

import jakarta.mail.search.ComparisonTerm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class NowSearchTermTest {

    private static final long ONE_MINUTE = 60 * 1000L;

    @Test
    public void testEqualTermsAreEqual() {
        NowSearchTerm term = new NowSearchTerm(ComparisonTerm.LE, true, ONE_MINUTE);
        NowSearchTerm same = new NowSearchTerm(ComparisonTerm.LE, true, ONE_MINUTE);

        assertEquals(term, same);
        assertEquals(term.hashCode(), same.hashCode());
    }

    @Test
    public void testDifferentComparisonIsNotEqual() {
        // the comparison operator is held by ComparisonTerm, so equals must take super.equals into account
        NowSearchTerm le = new NowSearchTerm(ComparisonTerm.LE, true, ONE_MINUTE);
        NowSearchTerm ge = new NowSearchTerm(ComparisonTerm.GE, true, ONE_MINUTE);

        assertNotEquals(le, ge);
        assertNotEquals(le.hashCode(), ge.hashCode());
    }

    @Test
    public void testDifferentOffsetIsNotEqual() {
        NowSearchTerm oneMinute = new NowSearchTerm(ComparisonTerm.LE, true, ONE_MINUTE);
        NowSearchTerm twoMinutes = new NowSearchTerm(ComparisonTerm.LE, true, 2 * ONE_MINUTE);

        assertNotEquals(oneMinute, twoMinutes);
        assertNotEquals(oneMinute.hashCode(), twoMinutes.hashCode());
    }

    @Test
    public void testDifferentSentDateIsNotEqual() {
        NowSearchTerm sent = new NowSearchTerm(ComparisonTerm.LE, true, ONE_MINUTE);
        NowSearchTerm received = new NowSearchTerm(ComparisonTerm.LE, false, ONE_MINUTE);

        assertNotEquals(sent, received);
        assertNotEquals(sent.hashCode(), received.hashCode());
    }
}
