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
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.SupportLevel;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackageLanguageMojoTest {
    private static final String GENERIC_MODEL
            = """
                    {
                      "model": {"kind":"language", "name":"language", "title":"Language", "description":"Generic language",
                                "label":"language,core", "firstVersion":"1.0.0", "javaType":"org.apache.camel.model.language.LanguageExpression"},
                      "properties": {
                        "language": {"kind":"attribute", "type":"string", "javaType":"java.lang.String", "required":true},
                        "expression": {"kind":"value", "type":"string", "javaType":"java.lang.String", "required":true},
                        "trim": {"kind":"attribute", "type":"boolean", "javaType":"java.lang.Boolean", "defaultValue":true}
                      }
                    }
                    """;

    @Test
    void genericLanguageUsesExistingModelAndItsOwnMetadata() {
        LanguageModel model = extract(GenericLanguage.class);
        assertEquals("custom", model.getName());
        assertEquals("Custom", model.getTitle());
        assertEquals("Custom language description", model.getDescription());
        assertEquals("language,ai", model.getLabel());
        assertEquals("4.23.0", model.getFirstVersion());
        assertEquals(SupportLevel.Preview, model.getSupportLevel());
        assertEquals("language", model.getModelName());
        assertEquals("org.apache.camel.model.language.LanguageExpression", model.getModelJavaType());
        assertEquals(GenericLanguage.class.getCanonicalName(), model.getJavaType());
        assertEquals(3, model.getOptions().size());
        assertEquals("language", model.getOptions().get(0).getName());
        assertEquals("expression", model.getOptions().get(1).getName());
        assertEquals("value", model.getOptions().get(1).getKind());
        assertEquals("language", PackageLanguageMojo.modelName("custom", GenericLanguage.class));
    }

    @Test
    void existingLanguagesKeepTheirModelSelectionAndMetadata() {
        assertEquals("custom", PackageLanguageMojo.modelName("custom", DedicatedLanguage.class));
        assertEquals("method", PackageLanguageMojo.modelName("bean", DedicatedLanguage.class));
        assertEquals("simple", PackageLanguageMojo.modelName("file", DedicatedLanguage.class));
        LanguageModel model = extract(DedicatedLanguage.class);
        assertEquals("Language", model.getTitle());
        assertEquals("Generic language", model.getDescription());
        assertEquals("language,core", model.getLabel());
    }

    @Test
    void genericLanguageRequiresDescriptiveMetadata() {
        assertThrows(IllegalArgumentException.class, () -> extract(IncompleteLanguage.class));
    }

    private LanguageModel extract(Class<?> type) {
        MavenProject project = new MavenProject();
        project.setGroupId("org.apache.camel");
        project.setArtifactId("camel-custom");
        project.setVersion("4.23.0-SNAPSHOT");
        PackageLanguageMojo mojo = new PackageLanguageMojo(new SystemStreamLog(), project, null, null, null, null, null) {
            @Override
            protected Class<?> loadClass(String name) {
                return type;
            }
        };
        return mojo.extractLanguageModel(project, GENERIC_MODEL, "custom", type);
    }

    @Language(value = "custom", modelName = "language")
    @Metadata(title = "Custom", description = "Custom language description", label = "language,ai", firstVersion = "4.23.0")
    static class GenericLanguage {
    }

    @Language("custom")
    @Metadata(title = "Must not replace core metadata")
    static class DedicatedLanguage {
    }

    @Language(value = "custom", modelName = "language")
    static class IncompleteLanguage {
    }
}
