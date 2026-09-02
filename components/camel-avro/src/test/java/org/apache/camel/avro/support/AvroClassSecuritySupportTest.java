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
package org.apache.camel.avro.support;

import java.io.IOException;
import java.util.UUID;

import org.apache.avro.Schema;
import org.apache.avro.util.ClassSecurityValidator;
import org.apache.camel.dataformat.avro.example.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroClassSecuritySupportTest {

    @BeforeEach
    void resetValidator() {
        AvroClassSecuritySupport.resetForTesting();
    }

    @Test
    void shouldRejectUntrustedApplicationClassesByDefault() {
        SecurityException exception = assertThrows(SecurityException.class,
                () -> ClassSecurityValidator.validate(Value.class));
        assertTrue(exception.getMessage().contains("org.apache.camel.dataformat.avro.example.Value"));
    }

    @Test
    void shouldTrustConfiguredPackages() {
        AvroClassSecuritySupport.trustPackages("org.apache.camel.dataformat.avro.example");

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Value.class));
    }

    @Test
    void shouldTrustClassNamePackage() {
        AvroClassSecuritySupport.trustClassName(Value.class.getName());

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Value.class));
    }

    @Test
    void shouldTrustParentPackageWhenChildPackageIsAlsoTrusted() {
        AvroClassSecuritySupport.trustPackages("org.apache.camel.dataformat.avro.example");
        AvroClassSecuritySupport.trustPackages("org.apache.camel.dataformat.avro.example.nested");

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(
                org.apache.camel.dataformat.avro.example.nested.NestedFoo.class));
        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Value.class));
    }

    @Test
    void shouldRejectWildcardPackages() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AvroClassSecuritySupport.trustPackages("*"));
        assertTrue(exception.getMessage().contains("Wildcard"));
    }

    @Test
    void shouldPreserveExistingGlobalValidator() {
        ClassSecurityValidator.ClassSecurityPredicate custom = clazz -> clazz == String.class;
        ClassSecurityValidator.setGlobal(custom);

        AvroClassSecuritySupport.trustPackages("org.apache.camel.dataformat.avro.example");

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(String.class));
        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Value.class));
    }

    @Test
    void shouldPreserveValidatorInstalledAfterFirstTrustCall() {
        AvroClassSecuritySupport.trustPackages("a.b");
        ClassSecurityValidator.setGlobal(
                ClassSecurityValidator.composite(ClassSecurityValidator.getGlobal(), c -> c == UUID.class));

        AvroClassSecuritySupport.trustPackages("c.d");

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(UUID.class));
    }

    @Test
    void shouldTrustNonNamedRootSchemaGraph() {
        Schema arraySchema = Schema.createArray(Value.SCHEMA$);

        assertDoesNotThrow(() -> AvroClassSecuritySupport.trustSchema(arraySchema));
        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Value.class));
    }

    @Test
    void shouldNotTrustSystemPackagesFromClassName() {
        AvroClassSecuritySupport.trustClassName(IOException.class.getName());

        assertThrows(SecurityException.class, () -> ClassSecurityValidator.validate(java.io.ObjectInputStream.class));
    }

    @Test
    void shouldTrustExactClassWithoutPackageWhenUsingClassNameOnly() {
        AvroClassSecuritySupport.trustClassNameOnly(Value.class.getName());

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Value.class));
        assertThrows(SecurityException.class,
                () -> ClassSecurityValidator.validate(org.apache.camel.dataformat.avro.example.DateRecord.class));
    }
}
