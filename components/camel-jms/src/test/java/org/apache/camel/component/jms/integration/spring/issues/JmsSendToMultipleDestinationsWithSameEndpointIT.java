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

package org.apache.camel.component.jms.integration.spring.issues;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.apache.camel.ExchangePattern;
import org.apache.camel.component.jms.JmsConstants;
import org.apache.camel.component.jms.integration.spring.SpringJMSBasic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This one does not run well in parallel: it becomes flaky
@Tags({@Tag("not-parallel")})
public class JmsSendToMultipleDestinationsWithSameEndpointIT extends SpringJMSBasic {

    private static final Logger LOG = LoggerFactory.getLogger(JmsSendToMultipleDestinationsWithSameEndpointIT.class);
    private static final String URI =
            "activemq:queue:JmsSendToMultipleDestinationsWithSameEndpointIT?autoStartup=false";

    @Test
    public void testSendToAlotOfMessageToQueues() {
        assertDoesNotThrow(this::sendToAlotOfMessagesToQueue);
    }

    private void sendToAlotOfMessagesToQueue() {
        int size = 100;

        LOG.info("About to send {} messages", size);

        for (int i = 0; i < size; i++) {
            // use the same endpoint but provide a header with the dynamic queue we send to
            // this allows us to reuse endpoints and not create a new endpoint for each and every jms queue
            // we send to
            if (i > 0 && i % 50 == 0) {
                LOG.info("Sent {} messages so far", i);
            }
            template.sendBodyAndHeader(
                    URI,
                    ExchangePattern.InOnly,
                    "Hello " + i,
                    JmsConstants.JMS_DESTINATION_NAME,
                    "JmsSendToMultipleDestinationsWithSameEndpointIT" + i);
        }

        LOG.info("Send complete use jconsole to view");
    }
}
