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
package org.apache.camel.catalog;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelCatalogMatchEndpointIdentityTest {

    static CamelCatalog catalog;

    @BeforeAll
    public static void setup() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    void testSameUriReturnsTrue() {
        assertTrue(catalog.matchEndpointIdentity("seda:work", "seda:work"));
    }

    @Test
    void testDifferentSchemeReturnsFalse() {
        assertFalse(catalog.matchEndpointIdentity("seda:work", "direct:work"));
    }

    @Test
    void testDifferentPathReturnsFalse() {
        assertFalse(catalog.matchEndpointIdentity("seda:foo", "seda:bar"));
    }

    @Test
    void testNonIdentityQueryParamsIgnored() {
        // seda has no endpointIdentity params, so query params are ignored
        assertTrue(catalog.matchEndpointIdentity("seda:work?concurrentConsumers=5", "seda:work?concurrentConsumers=10"));
    }

    @Test
    void testCouchbaseSameBucketMatches() {
        // couchbase has bucket as endpointIdentity
        assertTrue(catalog.matchEndpointIdentity(
                "couchbase:http://host:8091?bucket=orders&timeout=5000",
                "couchbase:http://host:8091?bucket=orders&timeout=10000"));
    }

    @Test
    void testCouchbaseDifferentBucketDoesNotMatch() {
        assertFalse(catalog.matchEndpointIdentity(
                "couchbase:http://host:8091?bucket=orders",
                "couchbase:http://host:8091?bucket=invoices"));
    }

    @Test
    void testMongodbSameDatabaseAndCollectionMatches() {
        // mongodb has database and collection as endpointIdentity
        assertTrue(catalog.matchEndpointIdentity(
                "mongodb:myDb?database=testdb&collection=users&readPreference=primary",
                "mongodb:myDb?database=testdb&collection=users&readPreference=secondary"));
    }

    @Test
    void testMongodbDifferentCollectionDoesNotMatch() {
        assertFalse(catalog.matchEndpointIdentity(
                "mongodb:myDb?database=testdb&collection=users",
                "mongodb:myDb?database=testdb&collection=orders"));
    }

    @Test
    void testMongodbDifferentDatabaseDoesNotMatch() {
        assertFalse(catalog.matchEndpointIdentity(
                "mongodb:myDb?database=db1&collection=users",
                "mongodb:myDb?database=db2&collection=users"));
    }

    @Test
    void testIdentityParamAbsentOnBothSidesMatches() {
        // both URIs omit the identity param — they match
        assertTrue(catalog.matchEndpointIdentity(
                "couchbase:http://host:8091",
                "couchbase:http://host:8091"));
    }

    @Test
    void testIdentityParamPresentOnOnlyOneSideDoesNotMatch() {
        assertFalse(catalog.matchEndpointIdentity(
                "couchbase:http://host:8091?bucket=orders",
                "couchbase:http://host:8091"));
    }

    @Test
    void testNullUriReturnsFalse() {
        assertFalse(catalog.matchEndpointIdentity(null, "seda:work"));
        assertFalse(catalog.matchEndpointIdentity("seda:work", null));
        assertFalse(catalog.matchEndpointIdentity(null, null));
    }
}
