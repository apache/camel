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

import org.junit.jupiter.api.Test;

import static org.apache.camel.component.file.MoveExistingFileStrategyUtils.completePartialRelativePath;
import static org.apache.camel.component.file.MoveExistingFileStrategyUtils.completeRelativePathInDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveExistingFileStrategyUtilsTest {

    @Test
    void nameThatStartsWithTheDirectoryNameIsPutInTheDirectory() {
        assertEquals("move/movement.txt.bak", completeRelativePathInDirectory("movement.txt.bak", "movement.txt", "move"));
        assertEquals("move/move-old/a.txt", completeRelativePathInDirectory("move-old/a.txt", "a.txt", "move"));
        assertEquals("data/in/data/inbox/a.txt", completeRelativePathInDirectory("data/inbox/a.txt", "a.txt", "data/in"));
    }

    @Test
    void pathInTheDirectoryIsUnchanged() {
        assertEquals("move/backup/hello.txt", completeRelativePathInDirectory("move/backup/hello.txt", "hello.txt", "move"));
        assertEquals("move\\backup\\hello.txt",
                completeRelativePathInDirectory("move\\backup\\hello.txt", "hello.txt", "move"));
        assertEquals("move", completeRelativePathInDirectory("move", "hello.txt", "move"));
        assertEquals("data/in/hello.txt.bak",
                completeRelativePathInDirectory("data/in/hello.txt.bak", "hello.txt", "data/in"));
        assertEquals("/archive/hello.txt", completeRelativePathInDirectory("/archive/hello.txt", "hello.txt", "move"));
    }

    @Test
    void otherRelativePathIsPutInTheDirectory() {
        assertEquals("move/backup/hello.txt", completeRelativePathInDirectory("backup/hello.txt", "hello.txt", "move"));
        assertEquals("move/backup/hello.txt", completeRelativePathInDirectory("backup/", "hello.txt", "move"));
        assertEquals("backup/hello.txt", completeRelativePathInDirectory("backup/hello.txt", "hello.txt", null));
    }

    @Test
    void completePartialRelativePathKeepsAPathThatStartsWithTheDirectoryName() {
        // the file component: ${file:parent}-old/${file:onlyname} is a directory next to the directory of the file
        assertEquals("data/in-old/a.txt", completePartialRelativePath("data/in-old/a.txt", "a.txt", "data/in"));
        assertEquals("data/in/backup/a.txt", completePartialRelativePath("backup/a.txt", "a.txt", "data/in"));
        assertEquals("data/in/backup/a.txt", completePartialRelativePath("data/in/backup/a.txt", "a.txt", "data/in"));
        assertEquals("backup/a.txt", completePartialRelativePath("backup/a.txt", "a.txt", null));
    }
}
