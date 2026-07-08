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
package org.apache.camel.component.azure.common;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AzureFileNameHelperTest {

    @Test
    void shouldResolveSimpleNameWithinDirectory(@TempDir Path dir) {
        final File result = AzureFileNameHelper.resolveWithinDirectory(dir.toString(), "file.txt");

        assertEquals(new File(dir.toFile(), "file.txt"), result);
        assertTrue(result.toPath().normalize().startsWith(dir));
    }

    @Test
    void shouldResolveNestedNameWithinDirectory(@TempDir Path dir) {
        final File result = AzureFileNameHelper.resolveWithinDirectory(dir.toString(), "sub/dir/file.txt");

        assertTrue(result.toPath().normalize().startsWith(dir));
    }

    @Test
    void shouldAllowInternalDotDotThatStaysWithinDirectory(@TempDir Path dir) {
        // "sub/../file.txt" normalizes back to "file.txt" inside the directory, so it must be accepted
        final File result = AzureFileNameHelper.resolveWithinDirectory(dir.toString(), "sub/../file.txt");

        assertTrue(result.toPath().normalize().startsWith(dir));
    }

    @Test
    void shouldRejectParentTraversal(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> AzureFileNameHelper.resolveWithinDirectory(dir.toString(), "../escaped/PROOF_PWNED"));
    }

    @Test
    void shouldRejectDeepParentTraversal(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> AzureFileNameHelper.resolveWithinDirectory(dir.toString(), "../../../../etc/cron.d/evil"));
    }

    @Test
    void shouldRejectSiblingDirectoryWithSharedPrefix(@TempDir Path parent) {
        // fileDir=<parent>/work; the name escapes to the sibling <parent>/workspace which shares the "work" prefix.
        // A bare string-prefix check would wrongly accept this; a path-segment boundary check rejects it.
        final String fileDir = parent.resolve("work").toString();

        assertThrows(IllegalArgumentException.class,
                () -> AzureFileNameHelper.resolveWithinDirectory(fileDir, "../workspace/secret"));
    }
}
