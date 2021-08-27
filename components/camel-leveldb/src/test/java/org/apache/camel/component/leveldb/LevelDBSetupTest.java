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

package org.apache.camel.component.leveldb;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBSetupTest extends CamelTestSupport {
    /**
     * The Level db file.
     */
    private LevelDBFile levelDBFile;

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        deleteDirectory("leveldb.dat");
        super.tearDown();
    }

    /**
     * Test level db start with no path.
     */
    @Test
    public void testLevelDBStartWithNoPath() {
        Assertions.assertDoesNotThrow(() -> runTest("leveldb.dat"));
    }

    private void runTest(String fileName) {
        levelDBFile = new LevelDBFile();
        levelDBFile.setFileName(fileName);
        levelDBFile.start();
        levelDBFile.stop();
    }

    /**
     * Test level db start with path.
     */
    @Test
    public void testLevelDBStartWithPath() {
        deleteDirectory("target/data");
        Assertions.assertDoesNotThrow(() -> runTest("target/data/leveldb.dat"));
    }
}
