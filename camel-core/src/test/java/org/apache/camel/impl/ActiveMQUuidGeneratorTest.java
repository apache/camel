/**
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

import junit.framework.TestCase;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ActiveMQUuidGeneratorTest extends TestCase {
    
    private static final Log LOG = LogFactory.getLog(ActiveMQUuidGeneratorTest.class);
    private static final String PATTERN = "^ID-.*/\\d{4,5}-\\d{13}/\\d{1}-\\d{1,10}$";

    public void testGenerateUUID() {
        ActiveMQUuidGenerator uuidGenerator = new ActiveMQUuidGenerator();

        String firstUUID = uuidGenerator.generateUuid();
        String secondUUID = uuidGenerator.generateUuid();

        assertTrue(firstUUID.matches(PATTERN));
        assertTrue(secondUUID.matches(PATTERN));
        assertFalse(firstUUID.equals(secondUUID));
    }

    public void testPerformance() {
        ActiveMQUuidGenerator uuidGenerator = new ActiveMQUuidGenerator();
        StopWatch watch = new StopWatch();

        LOG.info("First id: " + uuidGenerator.generateUuid());
        for (int i = 0; i < 500000; i++) {
            uuidGenerator.generateUuid();
        }
        LOG.info("Last id:  " + uuidGenerator.generateUuid());

        LOG.info("Took " + TimeUtils.printDuration(watch.stop()));
    }

}