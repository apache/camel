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
package org.apache.camel.component.openfga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFgaIdentifiersTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "user:anne", "document:budget", "team:eng#member", "user:anne@example.com",
            "document:2026/reports/q3" })
    void acceptsAWellFormedIdentifier(String value) {
        assertThat(OpenFgaIdentifiers.validate(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "anne", ":anne", "user:", "user", "user anne", "user:an ne", "user:anne\n",
            "user:anne\u0000" })
    void rejectsAnIdentifierThatIsNotTypeColonId(String value) {
        assertThat(OpenFgaIdentifiers.validate(value)).isEqualTo("invalid-identifier");
    }

    @Test
    void rejectsAnIdentifierLongerThanOpenFgaAccepts() {
        String tooLong = "user:" + "a".repeat(OpenFgaIdentifiers.MAX_LENGTH);

        assertThat(OpenFgaIdentifiers.validate(tooLong)).isEqualTo("invalid-identifier");
        assertThat(OpenFgaIdentifiers.validate("user:" + "a".repeat(OpenFgaIdentifiers.MAX_LENGTH - 5))).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "user:*", "employee:*" })
    void rejectsATypedWildcardAsTheSubjectOfACheck(String value) {
        // verified against OpenFGA 1.21.0: check(user:*, reader, document:public) answers true wherever a
        // public-access tuple exists, so a wildcard subject would grant every publicly shared object
        assertThat(OpenFgaIdentifiers.validate(value)).isEqualTo("wildcard-subject");
    }

    @ParameterizedTest
    @ValueSource(strings = { "user:*", "employee:*" })
    void acceptsATypedWildcardInARelationshipTuple(String value) {
        // writing user:* is exactly how a resource is shared with everyone, and that is the route's decision to make
        assertThat(OpenFgaIdentifiers.validateTupleValue(value)).isNull();
    }

    @Test
    void doesNotMistakeAUsersetForAWildcard() {
        // team:eng#member names a concrete userset; only the id part being * is a wildcard
        assertThat(OpenFgaIdentifiers.validate("team:*#member")).isEqualTo("wildcard-subject");
        assertThat(OpenFgaIdentifiers.validate("team:eng#member")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "reader", "can_view", "owner" })
    void acceptsAWellFormedRelation(String value) {
        assertThat(OpenFgaIdentifiers.validateRelation(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "read er", "reader\n", "authz:reader", "team#member", "reader@v2" })
    void rejectsARelationNameOpenFgaWouldNotAccept(String value) {
        assertThat(OpenFgaIdentifiers.validateRelation(value)).isEqualTo("invalid-relation");
    }

    @Test
    void recognisesAPrefixWhoseExpressionResolvedToNothing() {
        // "user:${exchangeProperty.subject}" on an exchange with no subject leaves exactly this, and it is the most
        // common misconfiguration there is - so it is reported as a missing identity, not as a malformed one
        assertThat(OpenFgaIdentifiers.hasBlankId("user:")).isTrue();
        assertThat(OpenFgaIdentifiers.hasBlankId("document:")).isTrue();
        assertThat(OpenFgaIdentifiers.hasBlankId("user:anne")).isFalse();
        assertThat(OpenFgaIdentifiers.hasBlankId("anne")).isFalse();
        assertThat(OpenFgaIdentifiers.hasBlankId(":anne")).isFalse();
    }

    @Test
    void reportsAnAbsentRelationDistinctlyFromAMalformedOne() {
        assertThat(OpenFgaIdentifiers.validateRelation(null)).isEqualTo("missing-relation");
        assertThat(OpenFgaIdentifiers.validateRelation("")).isEqualTo("missing-relation");
    }
}
