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
package org.apache.camel.component.debezium.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The files of the position (the file offset store) and of the schema history must be writable when the consumer
 * starts, else every save of the position fails with only a WARN, and a restart sends everything again.
 */
class EmbeddedDebeziumStorageFilesTest {

    private static final String FILE_HISTORY = "io.debezium.storage.file.history.FileSchemaHistory";

    @TempDir
    Path dir;

    private static TestEmbeddedDebeziumConfiguration configuration(String offsetFile) {
        TestEmbeddedDebeziumConfiguration configuration = new TestEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setTestField("test_field");
        configuration.setOffsetStorageFileName(offsetFile);
        return configuration;
    }

    @Test
    void anOffsetFileInAFolderThatExists() throws Exception {
        assertTrue(configuration(dir.resolve("offsets.dat").toString()).validateConfiguration().isValid());

        // a file that exists already
        Path file = Files.writeString(dir.resolve("existing.dat"), "");
        assertTrue(configuration(file.toString()).validateConfiguration().isValid());
    }

    @Test
    void anOffsetFileInAFolderThatDoesNotExist() {
        Path file = dir.resolve("missing/offsets.dat");

        ConfigurationValidation validation = configuration(file.toString()).validateConfiguration();

        assertFalse(validation.isValid());
        assertTrue(validation.getReason().contains("offsetStorageFileName (" + file + ")"), validation.getReason());
        assertTrue(validation.getReason().contains("which does not exist"), validation.getReason());
    }

    @Test
    void anOffsetFileInAFolderThatCannotBeWritten() throws Exception {
        Path folder = Files.createDirectory(dir.resolve("read-only"));
        assumeFalse(!folder.toFile().setWritable(false) || Files.isWritable(folder), "the folder stays writable (root)");
        try {
            ConfigurationValidation validation
                    = configuration(folder.resolve("offsets.dat").toString()).validateConfiguration();

            assertFalse(validation.isValid());
            assertTrue(validation.getReason().contains("which cannot be written"), validation.getReason());
        } finally {
            folder.toFile().setWritable(true);
        }
    }

    @Test
    void anOffsetFileThatIsAFolder() {
        ConfigurationValidation validation = configuration(dir.toString()).validateConfiguration();

        assertFalse(validation.isValid());
        assertTrue(validation.getReason().contains("is a folder"), validation.getReason());
    }

    @Test
    void theOffsetFileOfTheAdditionalPropertiesIsChecked() {
        // the engine gets the additional properties too
        TestEmbeddedDebeziumConfiguration configuration = configuration(dir.resolve("offsets.dat").toString());
        configuration.setAdditionalProperties(
                Map.of("offset.storage.file.filename", dir.resolve("missing/offsets.dat").toString()));

        assertFalse(configuration.validateConfiguration().isValid());
    }

    @Test
    void anotherOffsetStoreHasNoFile() {
        TestEmbeddedDebeziumConfiguration configuration = configuration(dir.resolve("missing/offsets.dat").toString());
        configuration.setOffsetStorage("org.apache.kafka.connect.storage.KafkaOffsetBackingStore");

        assertTrue(configuration.validateConfiguration().isValid());
    }

    @Test
    void theFileSchemaHistoryCreatesItsFolders() {
        TestEmbeddedDebeziumConfiguration configuration = configuration(dir.resolve("offsets.dat").toString());
        configuration.setAdditionalProperties(Map.of("schema.history.internal", FILE_HISTORY,
                "schema.history.internal.file.filename", dir.resolve("a/b/history.dat").toString()));

        assertTrue(configuration.validateConfiguration().isValid());
    }

    @Test
    void theFileSchemaHistoryNeedsAFile() {
        TestEmbeddedDebeziumConfiguration configuration = configuration(dir.resolve("offsets.dat").toString());
        configuration.setAdditionalProperties(Map.of("schema.history.internal", FILE_HISTORY));

        ConfigurationValidation validation = configuration.validateConfiguration();

        assertFalse(validation.isValid());
        assertTrue(validation.getReason().contains("schemaHistoryInternalFileFilename"), validation.getReason());
    }

    @Test
    void theFileSchemaHistoryInAFolderThatCannotBeCreated() throws Exception {
        Path folder = Files.createDirectory(dir.resolve("read-only"));
        assumeFalse(!folder.toFile().setWritable(false) || Files.isWritable(folder), "the folder stays writable (root)");
        try {
            TestEmbeddedDebeziumConfiguration configuration = configuration(dir.resolve("offsets.dat").toString());
            configuration.setAdditionalProperties(Map.of("schema.history.internal", FILE_HISTORY,
                    "schema.history.internal.file.filename", folder.resolve("a/history.dat").toString()));

            ConfigurationValidation validation = configuration.validateConfiguration();

            assertFalse(validation.isValid());
            assertTrue(validation.getReason().contains("cannot be created"), validation.getReason());
        } finally {
            folder.toFile().setWritable(true);
        }
    }
}
