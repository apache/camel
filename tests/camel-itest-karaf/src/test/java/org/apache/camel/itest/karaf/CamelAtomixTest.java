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
public class CamelAtomixTest extends BaseKarafTest {

    public static final String COMPONENT = extractName(CamelAtomixTest.class);

    @Test
    public void testMap() throws Exception {
        testComponent(COMPONENT, "atomix-map");
    }

    @Test
    public void testMultiMap() throws Exception {
        testComponent(COMPONENT, "atomix-multimap");
    }

    @Test
    public void testSet() throws Exception {
        testComponent(COMPONENT, "atomix-set");
    }

    @Test
    public void testQueue() throws Exception {
        testComponent(COMPONENT, "atomix-queue");
    }

    @Test
    public void testValue() throws Exception {
        testComponent(COMPONENT, "atomix-value");
    }

    @Test
    public void testMessaging() throws Exception {
        testComponent(COMPONENT, "atomix-messaging");
    }

}
