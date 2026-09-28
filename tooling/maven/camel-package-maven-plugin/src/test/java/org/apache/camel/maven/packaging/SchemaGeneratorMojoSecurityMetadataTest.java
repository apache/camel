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
package org.apache.camel.maven.packaging;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SchemaGeneratorMojo} builds the options of every model, dataformat and language. It used to drop the security
 * attributes of {@code @Metadata}, which silently kept those options out of the generated {@code SecurityUtils} and
 * {@code SensitiveUtils} tables - no prod-profile enforcement, no scanner coverage, no value masking, and nothing in
 * the build to signal it.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-25026">CAMEL-25026</a>
 */
class SchemaGeneratorMojoSecurityMetadataTest {

    @Test
    void testInsecureCategoryIsCopied() {
        EipOptionModel option = apply("insecureSerialization");

        assertEquals("insecure:serialization", option.getSecurity());
        assertFalse(option.isSecret(), "only the secret category implies a secret value");
    }

    @Test
    void testInsecureValueIsCopied() {
        EipOptionModel option = apply("insecureSsl");

        assertEquals("insecure:ssl", option.getSecurity());
        assertEquals("true", option.getInsecureValue());
    }

    @Test
    void testSecretCategoryImpliesSecret() {
        EipOptionModel option = apply("secretByCategory");

        assertEquals("secret", option.getSecurity());
        assertTrue(option.isSecret());
    }

    @Test
    void testSecretFlagImpliesSecretCategory() {
        EipOptionModel option = apply("secretByFlag");

        assertEquals("secret", option.getSecurity());
        assertTrue(option.isSecret());
    }

    @Test
    void testPlainOptionIsLeftAlone() {
        EipOptionModel option = apply("plain");

        assertNull(option.getSecurity());
        assertNull(option.getInsecureValue());
        assertFalse(option.isSecret());
    }

    @Test
    void testMissingAnnotationIsLeftAlone() {
        EipOptionModel option = new EipOptionModel();
        SchemaGeneratorMojo.applySecurityMetadata(option, null);

        assertNull(option.getSecurity());
        assertNull(option.getInsecureValue());
        assertFalse(option.isSecret());
    }

    private static EipOptionModel apply(String fieldName) {
        Metadata metadata;
        try {
            metadata = Options.class.getDeclaredField(fieldName).getAnnotation(Metadata.class);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
        EipOptionModel option = new EipOptionModel();
        SchemaGeneratorMojo.applySecurityMetadata(option, metadata);
        return option;
    }

    /**
     * Stands in for a model class such as {@code XMLSecurityDataFormat} or {@code AvroDataFormat}.
     */
    @SuppressWarnings("unused")
    private static final class Options {

        @Metadata(security = "insecure:serialization")
        private String insecureSerialization;

        @Metadata(security = "insecure:ssl", insecureValue = "true")
        private String insecureSsl;

        @Metadata(security = "secret")
        private String secretByCategory;

        @Metadata(secret = true)
        private String secretByFlag;

        @Metadata(description = "Nothing security related about this one")
        private String plain;
    }
}
