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
package org.apache.camel.impl;

import org.apache.camel.impl.engine.DefaultUuidGenerator;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultUuidGeneratorTest extends Assert {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultUuidGeneratorTest.class);

    @Test
    public void testGenerateUUID() {
        DefaultUuidGenerator uuidGenerator = new DefaultUuidGenerator();

        String firstUUID = uuidGenerator.generateUuid();
        String secondUUID = uuidGenerator.generateUuid();

        assertNotSame(firstUUID, secondUUID);
    }

    @Test
    public void testPerformance() {
        DefaultUuidGenerator uuidGenerator = new DefaultUuidGenerator();
        StopWatch watch = new StopWatch();

        LOG.info("First id: " + uuidGenerator.generateUuid());
        for (int i = 0; i < 500000; i++) {
            uuidGenerator.generateUuid();
        }
        LOG.info("Last id:  " + uuidGenerator.generateUuid());

        LOG.info("Took " + TimeUtils.printDuration(watch.taken()));
    }

    @Test
    public void testSanitizeHostName() throws Exception {
        assertEquals("somehost.lan", DefaultUuidGenerator.sanitizeHostName("somehost.lan"));
        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        assertEquals("otherhost.lan", DefaultUuidGenerator.sanitizeHostName("other\u0E08host.lan"));
    }

}
