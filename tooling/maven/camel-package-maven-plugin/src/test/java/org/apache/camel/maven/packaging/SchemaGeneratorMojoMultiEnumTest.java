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

import java.util.List;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25739: {@code @Metadata(multiEnum = true)} marks an option that takes several of its enum values, separated by
 * comma, such as the options of the jsonpath language.
 */
class SchemaGeneratorMojoMultiEnumTest {

    @Test
    void testMultiEnumIsCopied() {
        assertTrue(apply("several", List.of("A", "B")).isMultiEnum());
    }

    @Test
    void testOneValueEnumIsLeftAlone() {
        assertFalse(apply("one", List.of("A", "B")).isMultiEnum());
    }

    @Test
    void testFlagWithoutEnumsIsIgnored() {
        assertFalse(apply("noEnums", null).isMultiEnum());
    }

    private static EipOptionModel apply(String fieldName, List<String> enums) {
        Metadata metadata;
        try {
            metadata = Options.class.getDeclaredField(fieldName).getAnnotation(Metadata.class);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
        EipOptionModel option = new EipOptionModel();
        option.setEnums(enums);
        SchemaGeneratorMojo.applyMultiEnumMetadata(option, metadata);
        return option;
    }

    @SuppressWarnings("unused")
    private static final class Options {

        @Metadata(enums = "A,B", multiEnum = true)
        private String several;

        @Metadata(enums = "A,B")
        private String one;

        @Metadata(multiEnum = true)
        private String noEnums;
    }
}
