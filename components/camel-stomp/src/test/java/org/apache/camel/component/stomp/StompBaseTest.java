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
package org.apache.camel.component.stomp;

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;

public abstract class StompBaseTest extends CamelTestSupport {

    protected BrokerService brokerService;
    protected int numberOfMessages = 100;
    protected int port;
    private boolean canTest;

    protected int getPort() {
        return port;
    }

    /**
     * Whether we can test on this box, as not all boxes can be used for reliable CI testing.
     */
    protected boolean canTest() {
        return canTest;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable(61613);

        try {
            brokerService = new BrokerService();
            brokerService.setPersistent(false);
            brokerService.setAdvisorySupport(false);
            brokerService.addConnector("stomp://localhost:" + getPort() + "?trace=true");
            brokerService.start();
            brokerService.waitUntilStarted();
            super.setUp();
            canTest = true;
        } catch (Exception e) {
            System.err.println("Cannot test due " + e.getMessage() + " more details in the log");
            log.warn("Cannot test due " + e.getMessage(), e);
            canTest = false;
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (brokerService != null) {
            brokerService.stop();
            brokerService.waitUntilStopped();
        }
    }
}
