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
package org.apache.camel.itest.karaf;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class CamelHazelcastTest extends BaseKarafTest {

    public static final String COMPONENT = extractName(CamelHazelcastTest.class);

    @Test
    public void test() throws Exception {
        testComponent(COMPONENT, "hazelcast-atomicvalue");
        testComponent(COMPONENT, "hazelcast-instance");
        testComponent(COMPONENT, "hazelcast-list");
        testComponent(COMPONENT, "hazelcast-map");
        testComponent(COMPONENT, "hazelcast-multimap");
        testComponent(COMPONENT, "hazelcast-queue");
        testComponent(COMPONENT, "hazelcast-replicatedmap");
        testComponent(COMPONENT, "hazelcast-ringbuffer");
        testComponent(COMPONENT, "hazelcast-seda");
        testComponent(COMPONENT, "hazelcast-set");
        testComponent(COMPONENT, "hazelcast-topic");
    }

}
