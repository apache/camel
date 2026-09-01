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

import org.apache.avro.Schema;
import org.apache.avro.util.ClassSecurityValidator;
import org.apache.camel.dataformat.avro.example.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AvroClassSecuritySupportTest {

    @AfterEach
    void resetValidator() {
        System.clearProperty(AvroClassSecuritySupport.CAMEL_TRUSTED_PACKAGES_PROPERTY);
        ClassSecurityValidator.setGlobal(ClassSecurityValidator.DEFAULT);
    }

    @Test
    void shouldTrustAvroIpcClassesAfterEnsureAvroIpcPackagesTrusted() {
        AvroClassSecuritySupport.ensureAvroIpcPackagesTrusted();

        assertDoesNotThrow(() -> ClassSecurityValidator.validate(Schema.Type.class));
    }

    @Test
    void shouldRejectUntrustedApplicationClassesByDefault() {
        assertThrows(SecurityException.class, () -> ClassSecurityValidator.validate(Value.class));
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
    void shouldMergePackagesAcrossCalls() {
        AvroClassSecuritySupport.trustPackages("org.apache.camel.dataformat.avro.example");
        AvroClassSecuritySupport.ensureAvroIpcPackagesTrusted();

        assertDoesNotThrow(() -> {
            ClassSecurityValidator.validate(Value.class);
            ClassSecurityValidator.validate(Schema.Type.class);
        });
    }
}
