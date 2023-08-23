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

import java.nio.file.Files;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests to ensure that When the allowNullBody option is set to true it will create an empty file and not throw an
 * exception When the allowNullBody option is set to false it will throw an exception of "Cannot write null body to
 * file."
 */
public class FileProducerAllowNullBodyTest extends ContextTestSupport {

    @Test
    public void testAllowNullBodyTrue() throws Exception {
        template.sendBody(fileUri("?allowNullBody=true&fileName=allowNullBody.txt"), null);
        assertFileExists(testFile("allowNullBody.txt"));
    }

    @Test
    public void testAllowNullBodyFalse() {

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody(fileUri("?fileName=allowNullBody.txt"), null),
                "Should have thrown a GenericFileOperationFailedException");

        GenericFileOperationFailedException cause
                = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().endsWith("allowNullBody.txt"));

        assertFalse(Files.exists(testFile("allowNullBody.txt")),
                "allowNullBody set to false with null body should not create a new file");
    }
}
