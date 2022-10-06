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

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogWriterRollOverUpdateAsyncTest extends LogWriterRollOverUpdateAsyncBase {
    private static final Logger LOG = LoggerFactory.getLogger(LogWriterRollOverUpdateAsyncTest.class);

    protected void asyncGenerate() {
        try {
            LOG.trace("Generating ...");
            generateDataFilePredictable(e -> {
                LOG.debug("Putting into the queue: {}", e);
                entryInfos.add(e);
            }, logWriter);
            LOG.trace("Done generating records");
        } catch (IOException e) {
            LOG.error("Failed to generate records: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            latch.countDown();
        }
    }

    /*
     * This
     */
    @DisplayName("Test the async update process with no (significant) contention")
    @Test
    void testReadWriteUpdateRecordsWithRollOver() throws IOException, InterruptedException {
        runTest((int) RECORD_COUNT + 1);
    }

}
