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
package org.apache.camel.service.lra;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LRASagaServiceTest extends CamelTestSupport {

    public LRASagaServiceTest() {
        setUseRouteBuilder(false);
    }

    @DisplayName("Tests whether doStart() is creating a LRAClient")
    @Test
    void testCanCreateLRAClient() throws Exception {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        sagaService.setCamelContext(this.context());
        sagaService.doStart();

        LRAClient client = sagaService.getClient();
        Assertions.assertNotNull(client, "lraClient must not be null");
    }

    @DisplayName("Tests whether doStart() is creating an alternative LRAClient")
    @Test
    void testCanCreateAlternativeLRAClient() throws Exception {
        LRASagaService sagaService = new AlternativeLRASagaService();
        applyMockProperties(sagaService);
        sagaService.setCamelContext(this.context());
        sagaService.doStart();

        LRAClient client = sagaService.getClient();
        Assertions.assertNotNull(client, "lraClient must not be null");

        Assertions.assertInstanceOf(AlternativeLRAClient.class, client, "client must be an instance of AlternativeLRAClient");
    }

    private void applyMockProperties(LRASagaService sagaService) {
        sagaService.setCoordinatorUrl("mockCoordinatorUrl");
        sagaService.setLocalParticipantUrl("mockLocalParticipantUrl");
        sagaService.setLocalParticipantContextPath("mockLocalParticipantContextPath");
        sagaService.setCoordinatorContextPath("mockCoordinatorContextPath");
    }

    private class AlternativeLRASagaService extends LRASagaService {
        protected LRAClient createLRAClient() {
            return new AlternativeLRAClient(this);
        }
    }

    private class AlternativeLRAClient extends LRAClient {
        public AlternativeLRAClient(LRASagaService sagaService) {
            super(sagaService);
        }
    }

}
