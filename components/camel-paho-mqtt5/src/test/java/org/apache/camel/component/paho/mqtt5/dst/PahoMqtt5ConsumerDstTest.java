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
package org.apache.camel.component.paho.mqtt5.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-paho-mqtt5 consumer against a fake MQTT 5 broker and Paho client, with
 * connections closed by the broker, a broker that cannot be reached, connections lost during a subscribe, failed and
 * slow processing, and route stops and starts: a message is acknowledged only after its exchange completed (or failed
 * for good), the client drops no message, and once the faults cleared every message is routed. A failing seed is
 * replayed with {@code -Ddst.seed=<seed>}; {@code -Ddst.seeds=5000} runs more seeds.
 */
@DstInstrument("org.apache.camel.component.paho.mqtt5")
class PahoMqtt5ConsumerDstTest {

    @DstTest(seeds = 1000)
    void everyMessageIsRoutedAfterReconnects(Dst dst) throws Exception {
        new PahoMqtt5World(dst).run();
    }
}
