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
package org.apache.camel.dsl.yaml.validator;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A validator built for a schema document handed to it, as camel validate --camel-version reads the schema of another
 * Camel version from its camel-yaml-dsl jar (CAMEL-24711), validates against that document and not the classpath.
 */
public class YamlValidatorSchemaDocumentTest {

    private static String schema(String location) throws Exception {
        try (var is = YamlValidator.class.getResourceAsStream(location)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void theGivenDocumentIsTheSchema() throws Exception {
        File shorthand = new File("src/test/resources/canonical-invalid-log-shorthand.yaml");

        // the classpath schema accepts the log shorthand
        YamlValidator classpath = new YamlValidator(false, null, null);
        classpath.init();
        assertThat(classpath.validate(shorthand)).isEmpty();

        // the same validator given the canonical document as its schema rejects it: the document is what counts
        YamlValidator given = new YamlValidator(false, schema("/schema/camelYamlDsl-canonical.json"), null);
        given.init();
        assertThat(given.validate(shorthand)).isNotEmpty();
        assertThat(given.isCanonical()).isFalse();

        // and the classic document given explicitly validates as the classpath one does
        YamlValidator same = new YamlValidator(false, schema("/schema/camelYamlDsl.json"), null);
        same.init();
        assertThat(same.validate(shorthand)).isEmpty();
        assertThat(same.validate(new File("src/test/resources/canonical-valid.yaml"))).isEmpty();
    }
}
