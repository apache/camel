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

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.StreamCachingStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stream caching strategy can be stopped and started again without accumulating its configuration, and it removes
 * its spool directory also when spooling is only by used heap memory.
 */
public class StreamCachingStrategyRestartTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setStreamCaching(true);
        return context;
    }

    @Test
    public void testRestartDoesNotDuplicateAllowClasses() throws Exception {
        StreamCachingStrategy strategy = context.getStreamCachingStrategy();
        strategy.stop();
        strategy.setEnabled(true);
        strategy.setAllowClasses("java.lang.String");

        strategy.start();
        assertEquals(1, strategy.getAllowClasses().size());

        // stop and start the strategy again
        strategy.stop();
        strategy.start();
        assertEquals(1, strategy.getAllowClasses().size(), "the allow classes should not be added again on restart");
    }

    @Test
    public void testAllowClassesAndAllowClassNames() throws Exception {
        StreamCachingStrategy strategy = context.getStreamCachingStrategy();
        context.stop();
        // classes and class names can be combined
        strategy.setAllowClasses(Integer.class);
        strategy.setAllowClasses("java.lang.String");

        context.start();
        assertEquals(2, strategy.getAllowClasses().size());
    }

    @Test
    public void testSpoolDirectoryRemovedWhenSpoolingByHeapMemory() throws Exception {
        File dir = testDirectory("spool").toFile();
        CamelContext camel = new DefaultCamelContext();
        camel.setStreamCaching(true);
        StreamCachingStrategy strategy = camel.getStreamCachingStrategy();
        strategy.setSpoolEnabled(true);
        strategy.setSpoolDirectory(dir);
        // spool only by used heap memory
        strategy.setSpoolThreshold(0);
        strategy.setSpoolUsedHeapMemoryThreshold(1);

        camel.start();
        assertTrue(dir.exists(), "the spool directory should be created");

        camel.stop();
        assertFalse(dir.exists(), "the spool directory should be removed when stopping");
    }
}
