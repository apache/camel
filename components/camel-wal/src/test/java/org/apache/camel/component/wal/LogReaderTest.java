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

package org.apache.camel.component.wal;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogReaderTest extends LogTestBase {

    @Test
    public void testHeader() {
        Assertions.assertDoesNotThrow(() -> generateDataFilePredictable());
        File reportFile = new File(testDir, "test.data");
        Assumptions.assumeTrue(reportFile.exists());

        try (LogReader reader = new LogReader(reportFile)) {

            Header fileHeader = reader.getHeader();
            assertEquals(Header.FORMAT_NAME, fileHeader.getFormatName().trim());
            assertEquals(Header.CURRENT_FILE_VERSION, fileHeader.getFileVersion());
        } catch (IOException e) {
            Assertions.fail(e.getMessage());
        }
    }
}
