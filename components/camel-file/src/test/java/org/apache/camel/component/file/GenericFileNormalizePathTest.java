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

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for CAMEL-24415: a backslash is a legal file-name character on POSIX and must not be rewritten into a
 * path separator by {@link GenericFile#normalizePathToProtocol(String)}. Before the fix the name was split into a
 * multi-component relative path, and the delete/move/marker strategies then resolved a different path than the one that
 * was read.
 */
public class GenericFileNormalizePathTest {

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "On Windows a backslash is a path separator")
    public void backslashInFileNameIsPreservedOnPosix() {
        GenericFile<File> file = new GenericFile<>();

        // a single-component file name whose name legitimately contains backslashes
        file.setFileName("annual\\report.txt");
        assertEquals("annual\\report.txt", file.getFileName(),
                "backslash must be preserved as a file-name character, not turned into a separator");

        // when it appears inside a relative path, only the forward slash is the separator
        file.setRelativeFilePath("inbox/annual\\report.txt");
        assertEquals("inbox/annual\\report.txt", file.getRelativeFilePath());
    }
}
