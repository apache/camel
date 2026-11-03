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
import java.util.Locale;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.LanguageModel.LanguageOptionModel;
import org.apache.camel.tooling.model.SupportLevel;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void theOptionsOfTheLanguageAreItsFieldsWithMetadata() {
        List<LanguageOptionModel> options = PackageLanguageMojo.languageOptions(OptionsLanguage.class);
        assertEquals(List.of("memory", "mode", "flags", "factory", "base"),
                options.stream().map(LanguageOptionModel::getName).toList());

        LanguageOptionModel memory = options.get(0);
        assertEquals("property", memory.getKind());
        assertEquals("Memory", memory.getDisplayName());
        assertEquals("integer", memory.getType());
        assertEquals("long", memory.getJavaType());
        assertEquals(1024L, memory.getDefaultValue());
        assertEquals("advanced", memory.getGroup());
        assertEquals("advanced", memory.getLabel());
        assertEquals("The memory", memory.getDescription());
        assertNull(memory.getDeprecationNote());

        LanguageOptionModel mode = options.get(1);
        assertEquals("enum", mode.getType());
        assertEquals(List.of("FAST", "SAFE"), mode.getEnums());
        assertFalse(mode.isMultiEnum());
        assertEquals("common", mode.getGroup());
        assertNull(mode.getLabel());

        // an array of an enum is set as a comma separated list of its values
        LanguageOptionModel flags = options.get(2);
        assertEquals("enum", flags.getType());
        assertEquals(List.of("FAST", "SAFE"), flags.getEnums());
        assertTrue(flags.isMultiEnum());

        LanguageOptionModel factory = options.get(3);
        assertEquals("object", factory.getType());
        assertEquals(StringBuilder.class.getName(), factory.getJavaType());

        // the fields of a super class are options too
        assertEquals("base", options.get(4).getName());
    }

    @Test
    void aLanguageWithoutOptionsSaysSo() {
        assertEquals(List.of(), PackageLanguageMojo.languageOptions(GenericLanguage.class));
        assertEquals(List.of(), extract(GenericLanguage.class).getLanguageOptions());
    }

    @Test
    void anOptionOfTheLanguageRequiresADescription() {
        assertThrows(IllegalArgumentException.class, () -> PackageLanguageMojo.languageOptions(UndescribedLanguage.class));
    }

    @Test
    void theConfigurerSetsAndGetsTheOptions() throws Exception {
        String source = mojo(OptionsLanguage.class).generatePropertyConfigurer("org.example", "OptionsLanguageConfigurer",
                "org.apache.camel.support.component.PropertyConfigurerSupport", "OptionsLanguage", false,
                PackageLanguageMojo.languageOptions(OptionsLanguage.class));
        assertTrue(source.contains("public class OptionsLanguageConfigurer extends"), source);
        assertTrue(
                source.contains("case \"memory\": target.setMemory(property(camelContext, long.class, value)); return true;"),
                source);
        assertTrue(source.contains("case \"memory\": return target.getMemory();"), source);
        assertTrue(source.contains("case \"base\": target.setBase(property(camelContext, java.lang.String.class, value));"),
                source);
    }

    @Test
    void aLanguageWithoutOptionsHasAnEmptyConfigurer() throws Exception {
        String source = mojo(GenericLanguage.class).generatePropertyConfigurer("org.example", "GenericLanguageConfigurer",
                "org.apache.camel.support.component.PropertyConfigurerSupport", "GenericLanguage", false,
                PackageLanguageMojo.languageOptions(GenericLanguage.class));
        assertTrue(source.contains("public class GenericLanguageConfigurer extends"), source);
        assertFalse(source.contains("case \""), source);
    }

    @Test
    void theConfigurerDoesNotDependOnTheDefaultLocale() throws Exception {
        Locale oldLocale = Locale.getDefault();
        // the Turkish locale upper-cases i to a dotted capital I and lower-cases I to a dotless i
        Locale.setDefault(Locale.of("tr", "TR"));
        try {
            String source = mojo(LocaleLanguage.class).generatePropertyConfigurer("org.example", "LocaleLanguageConfigurer",
                    "org.apache.camel.support.component.PropertyConfigurerSupport", "LocaleLanguage", false,
                    PackageLanguageMojo.languageOptions(LocaleLanguage.class));
            assertTrue(source.contains("switch (ignoreCase ? name.toLowerCase(Locale.ROOT) : name) {"), source);
            assertTrue(source.contains("case \"itemid\":"), source);
            assertTrue(source.contains("case \"itemId\": target.setItemId("), source);
            assertTrue(source.contains("case \"itemId\": return target.getItemId();"), source);
        } finally {
            Locale.setDefault(oldLocale);
        }
    }

    private LanguageModel extract(Class<?> type) {
        PackageLanguageMojo mojo = mojo(type);
        return mojo.extractLanguageModel(mojo.project, GENERIC_MODEL, "custom", type);
    }

    private PackageLanguageMojo mojo(Class<?> type) {
        MavenProject project = new MavenProject();
        project.setGroupId("org.apache.camel");
        project.setArtifactId("camel-custom");
        project.setVersion("4.24.0-SNAPSHOT");
        return new PackageLanguageMojo(new SystemStreamLog(), project, null, null, null, null, null, null, null) {
            @Override
            protected Class<?> loadClass(String name) {
                return type;
            }
        };
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

    enum Mode {
        FAST,
        SAFE
    }

    static class BaseLanguage {
        @Metadata(description = "From the super class")
        private String base;
    }

    static class OptionsLanguage extends BaseLanguage {
        @Metadata(description = "Not an option")
        private static final String CONSTANT = "constant";

        @Metadata(label = "advanced", defaultValue = "1024", description = "The memory")
        private long memory = 1024;
        @Metadata(description = "The mode")
        private Mode mode;
        @Metadata(description = "The flags")
        private Mode[] flags;
        @Metadata(label = "advanced", description = "The factory")
        private StringBuilder factory;
        @Metadata(skip = true, description = "Skipped")
        private String skipped;
        private String internal;
    }

    static class UndescribedLanguage {
        @Metadata(label = "advanced")
        private String option;
    }

    static class LocaleLanguage {
        @Metadata(description = "The item id")
        private String itemId;
    }
}
