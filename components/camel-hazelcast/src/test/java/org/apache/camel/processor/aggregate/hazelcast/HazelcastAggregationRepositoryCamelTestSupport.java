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
package org.apache.camel.processor.aggregate.hazelcast;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.test.infra.common.TestEntityNameGenerator;
import org.apache.camel.test.infra.hazelcast.services.HazelcastService;
import org.apache.camel.test.infra.hazelcast.services.HazelcastServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HazelcastAggregationRepositoryCamelTestSupport extends CamelTestSupport {

    @RegisterExtension
    public static HazelcastService hazelcastService = HazelcastServiceFactory.createService();

    @RegisterExtension
    public static TestEntityNameGenerator nameGenerator = new TestEntityNameGenerator();

    private static HazelcastInstance hzOne;
    private static HazelcastInstance hzTwo;

    protected static HazelcastInstance getFirstInstance() {
        return hzOne;
    }

    protected static HazelcastInstance getSecondInstance() {
        return hzTwo;
    }

    @BeforeAll
    public static void setUpHazelcastCluster() {
        hzOne = Hazelcast.newHazelcastInstance(hazelcastService.createConfiguration(null, 0, "hzOne", "aggregation"));
        hzTwo = Hazelcast.newHazelcastInstance(hazelcastService.createConfiguration(null, 0, "hzTwo", "aggregation"));
    }

    @AfterAll
    public static void shutDownHazelcastCluster() {
        Hazelcast.shutdownAll();
    }
}
