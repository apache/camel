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
package org.apache.camel.processor.aggregate.hazelcast;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;


public class HazelcastAggregationRepositoryCamelTestSupport extends CamelTestSupport {
    private static HazelcastInstance hzOne;
    private static HazelcastInstance hzTwo;

    protected static void doInitializeHazelcastInstances() {
        hzOne = Hazelcast.newHazelcastInstance();
        hzTwo = Hazelcast.newHazelcastInstance();
    }

    protected static void doDestroyHazelcastInstances() {
        hzOne.getLifecycleService().shutdown();
        hzTwo.getLifecycleService().shutdown();
    }

    protected static HazelcastInstance getFirstInstance() {
        return hzOne;
    }

    protected static HazelcastInstance getSecondInstance() {
        return hzTwo;
    }

    @BeforeClass
    public static void setUpHazelcastCluster() {
        doInitializeHazelcastInstances();
    }

    @AfterClass
    public static void shutDownHazelcastCluster() {
        doDestroyHazelcastInstances();
    }
}
