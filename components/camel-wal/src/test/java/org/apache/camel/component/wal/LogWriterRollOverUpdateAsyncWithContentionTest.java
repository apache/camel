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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogWriterRollOverUpdateAsyncWithContentionTest extends LogWriterRollOverUpdateAsyncBase {
    private static final Logger LOG = LoggerFactory.getLogger(LogWriterRollOverUpdateAsyncWithContentionTest.class);

    protected void asyncGenerate() {
        try {
            LOG.trace("Generating ...");
            generateDataFilePredictable(e -> {
                try {
                    LOG.debug("Putting into the queue: {}", e);
                    entryInfos.put(e);
                } catch (InterruptedException ex) {
                    LOG.error("Interrupted while putting record into the queue: {}", ex.getMessage(), ex);
                    throw new RuntimeException(ex);
                }
            }, logWriter);
            LOG.trace("Done generating records");
        } catch (IOException e) {
            LOG.error("Failed to generate records: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            latch.countDown();
        }
    }

    @DisplayName("Test the async update process with different levels of contention")
    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 5, 10, 200, 500, 3000, 4000 })
    void testReadWriteUpdateRecordsWithRollOver(int queueCapacity) throws IOException, InterruptedException {
        runTest(queueCapacity);
    }

}
