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
package org.apache.camel.component.log;

import org.apache.camel.ContextTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class LogRouteTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(LogRouteTest.class);

    public void testSendMessageToLog() throws Exception {
        template.sendBody("log:org.apache.camel.TEST", "<level>default</level>");
    }

    public void testSendMessageToInfoLog() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?level=INFO", "<level>INFO</level>");
    }

    public void testSendMessageToWarnLog() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?level=warn", "<level>WARN</level>");
    }

    public void testSendMessageToBadLevel() throws Exception {
        try {
            template.sendBody("log:org.apache.camel.TEST?level=noSuchLevel", "<level>noSuchLevel</level>");
            fail("Should have failed!");
        } catch (Exception e) {
            LOG.debug("Caught expected exception: " + e, e);
        }
    }

}
