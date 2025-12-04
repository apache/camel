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

package org.apache.camel.component.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Unit tests to ensure that When the allowNullBody option is set to true it will create an empty file and not throw an
 * exception When the allowNullBody option is set to false it will throw an exception of "Cannot write null body to
 * file."
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileProducerAllowNullBodyTest extends ContextTestSupport {

    @Order(1)
    @Test
    @DisplayName("Tests that an empty file named allowNullBody.txt will be created")
    public void testAllowNullBodyTrue() {
        template.sendBody(fileUri("?allowNullBody=true&fileName=allowNullBody.txt"), null);

        final Path path = testFile("allowNullBody.txt");
        assertFileExists(path);

        final long size = path.toFile().length();
        assertEquals(0, size);
    }

    @Order(2)
    @Test
    @DisplayName("Tests that a non-empty file named allowNullBody.txt will be created and then truncated")
    public void testAllowNullBodyTrueTruncate() {
        template.sendBody(fileUri("?allowNullBody=true&fileName=allowNullBody.txt"), "Hello");

        final Path path = testFile("allowNullBody.txt");
        assertFileExists(path);

        final long sizeBeforeTruncate = path.toFile().length();
        assertNotEquals(0, sizeBeforeTruncate);

        template.sendBody(fileUri("?allowNullBody=true&fileName=allowNullBody.txt"), null);
        assertFileExists(path);

        final long sizeAfterTruncate = path.toFile().length();
        assertEquals(0, sizeAfterTruncate);
    }

    @Order(3)
    @Test
    @DisplayName("Tests that an exception will be thrown if allowNullBody is absent and a null body is sent")
    public void testAllowNullBodyFalse() {
        CamelExecutionException e = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBody(fileUri("?fileName=allowNullBody.txt"), null),
                "Should have thrown a GenericFileOperationFailedException");

        GenericFileOperationFailedException cause =
                assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().endsWith("allowNullBody.txt"));

        assertFalse(
                Files.exists(testFile("allowNullBody.txt")),
                "allowNullBody set to false with null body should not create a new file");
    }
}
