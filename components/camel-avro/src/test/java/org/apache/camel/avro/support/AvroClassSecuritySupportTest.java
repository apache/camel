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

import org.apache.avro.util.ClassSecurityValidator;
import org.apache.camel.dataformat.avro.example.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvroClassSecuritySupportTest {

    @BeforeEach
    void resetValidator() {
        AvroClassSecuritySupport.resetForTesting();
    }

    @Test
    void shouldRejectUntrustedApplicationClassesByDefault() {
        assertThatThrownBy(() -> ClassSecurityValidator.validate(Value.class))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("org.apache.camel.dataformat.avro.example.Value");
    }

    @Test
    void shouldTrustConfiguredPackages() {
        AvroClassSecuritySupport.trustPackages("org.apache.camel.dataformat.avro.example");

        assertThatCode(() -> ClassSecurityValidator.validate(Value.class)).doesNotThrowAnyException();
    }

    @Test
    void shouldTrustClassNamePackage() {
        AvroClassSecuritySupport.trustClassName(Value.class.getName());

        assertThatCode(() -> ClassSecurityValidator.validate(Value.class)).doesNotThrowAnyException();
    }

    @Test
    void shouldMergePackagesAcrossCalls() {
        AvroClassSecuritySupport.trustPackages("org.apache.camel.dataformat.avro.example");
        AvroClassSecuritySupport.trustPackages("org.apache.camel.dataformat.avro.example.extra");

        assertThatCode(() -> ClassSecurityValidator.validate(Value.class)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWildcardPackages() {
        assertThatThrownBy(() -> AvroClassSecuritySupport.trustPackages("*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wildcard");
    }
}
