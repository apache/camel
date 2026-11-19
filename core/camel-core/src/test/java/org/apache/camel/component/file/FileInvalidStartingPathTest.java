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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileInvalidStartingPathTest extends ContextTestSupport {

    @Test
    public void testInvalidStartingPath() {
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint(fileUri("${date:now:yyyyMMdd}/${in.header.messageType}-${date:now:hhmmss}.txt")),
                "Should have thrown an exception");

        assertTrue(e.getCause().getMessage().startsWith("Invalid directory"));
    }

    @Test
    void testInvalidStartingPathSaysHowToUseADynamicDirectory() {
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("file:archive/${header.month}"));

        assertEquals("Invalid directory: archive/${header.month}. Dynamic expressions with ${ } placeholders is not"
                     + " allowed. Use the fileName option to set the dynamic expression. The directory is fixed when the"
                     + " endpoint is created, so from, to and pollEnrich with a fixed uri do not evaluate it: use toD to"
                     + " write to a dynamic directory, or pollEnrich with a simple expression"
                     + " (pollEnrich().simple(\"...\") in Java) to read from one.",
                e.getCause().getMessage());
    }

    @Test
    void testValidStartingPath() {
        Endpoint endpoint = context.getEndpoint(
                fileUri("?fileName=${date:now:yyyyMMdd}/${in.header.messageType}-${date:now:hhmmss}.txt"));
        assertNotNull(endpoint, "Endpoint should be resolved for a valid starting path");
        FileEndpoint fileEndpoint = assertInstanceOf(FileEndpoint.class, endpoint);
        assertNotNull(fileEndpoint.getFileName(), "FileEndpoint should have a fileName expression set");
        assertNotNull(fileEndpoint.getConfiguration().getDirectory(),
                "FileEndpoint should have a directory configured");
        assertEquals("${date:now:yyyyMMdd}/${in.header.messageType}-${date:now:hhmmss}.txt",
                fileEndpoint.getFileName().toString(),
                "FileEndpoint fileName expression should match the configured value");
    }

}
