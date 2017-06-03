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
package org.apache.camel.component.jt400;

import org.junit.Before;
import org.junit.Test;

public class Jt400DataQueueProducerTest extends Jt400TestSupport {

    private static final String PASSWORD = "p4ssw0rd";

    private Jt400DataQueueProducer producer;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Jt400Endpoint endpoint = resolveMandatoryEndpoint(
                "jt400://user:" + PASSWORD + "@host/qsys.lib/library.lib/queue.dtaq?connectionPool=#mockPool",
                Jt400Endpoint.class);
        producer = new Jt400DataQueueProducer(endpoint);
    }

    @Test
    public void testToStringHidesPassword() {
        assertFalse(producer.toString().contains(PASSWORD));
    }

}
