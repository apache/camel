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
package org.apache.camel.processor;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.CamelLogger;
import org.easymock.EasyMock;
import org.slf4j.Logger;

public class ThroughPutLoggerTest extends TestCase {

    public void testLogStringDurationIsNotZero() throws Exception {
        CamelContext camel = new DefaultCamelContext();
        camel.start();

        Logger logger = EasyMock.createMock(Logger.class);
        logger.isInfoEnabled();
        EasyMock.expectLastCall().andReturn(true).atLeastOnce();
        logger.info(EasyMock.startsWith("Received: 10"));
        EasyMock.expectLastCall().once();
        logger.info(EasyMock.startsWith("Received: 20"));
        EasyMock.expectLastCall().once();
        EasyMock.replay(logger);
        ThroughputLogger underTest = new ThroughputLogger(new CamelLogger(logger));
        underTest.setGroupSize(10);
        for (int i = 0; i < 25; i++) {
            underTest.process(new DefaultExchange(camel));
        }
        EasyMock.verify(logger);

        camel.stop();
    }
}
