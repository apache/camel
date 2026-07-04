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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GenericFileHelperTest {

    private final File workDir = new File("target/localwork");

    @Test
    public void shouldAllowFilesWithinLocalWorkDirectory() {
        // a plain name, a nested name, and a ../ that still resolves within the work directory are all allowed
        assertDoesNotThrow(() -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "file.txt"), workDir));
        assertDoesNotThrow(() -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "sub/dir/file.txt"), workDir));
        assertDoesNotThrow(() -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "sub/../file.txt"), workDir));
    }

    @Test
    public void shouldRejectFilesEscapingLocalWorkDirectory() {
        // a remote file name that resolves outside the configured local work directory must be rejected
        assertThrows(GenericFileOperationFailedException.class,
                () -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "../escape.txt"), workDir));
        assertThrows(GenericFileOperationFailedException.class,
                () -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "../../etc/passwd"), workDir));
        assertThrows(GenericFileOperationFailedException.class,
                () -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "sub/../../escape.txt"), workDir));
    }
}
