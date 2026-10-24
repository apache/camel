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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericFileKeptDoneFilesTest {

    private final GenericFileKeptDoneFiles keptDoneFiles = new GenericFileKeptDoneFiles();

    @Test
    void cutListingKeepsTheKeptDoneFilesAtAllTimes() {
        keptDoneFiles.keep("dir/ready");
        keptDoneFiles.keep("other/ready");

        // a cut listing does not forget a kept done file, not even for a moment: a file of the last poll that completes
        // on another thread meanwhile must still find it kept, listed in this poll or not
        assertNull(keptDoneFiles.startBatch(Set.of("dir/ready"), false));
        assertTrue(keptDoneFiles.isKept("dir/ready"));
        assertTrue(keptDoneFiles.isKept("other/ready"));
    }

    @Test
    void completeListingForgetsTheKeptDoneFilesAndReturnsThoseOfGoneFiles() {
        keptDoneFiles.keep("dir/ready");
        keptDoneFiles.keep("gone/ready");

        assertEquals(List.of("gone/ready"), keptDoneFiles.startBatch(Set.of("dir/ready"), true));
        assertFalse(keptDoneFiles.isKept("dir/ready"));
        assertFalse(keptDoneFiles.isKept("gone/ready"));

        keptDoneFiles.keep("dir/ready");
        assertEquals(List.of("dir/ready"), keptDoneFiles.startBatch(Set.of(), true));
        assertFalse(keptDoneFiles.isKept("dir/ready"));
    }
}
