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

package org.apache.camel.spring.cloud;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootApplication
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        CamelCloudAutoConfiguration.class,
        CamelCloudServiceCallConfiguration.class
    },
    properties = {
        "camel.cloud.load-balancer.enabled=false",
        "camel.cloud.service-discovery.services[custom-svc-list]=localhost:9090,localhost:9091,localhost:9092",
        "camel.cloud.service-filter.blacklist[custom-svc-list]=localhost:9091",
        "ribbon.enabled=false",
        "debug=false"
    }
)
public class CamelCloudServiceCallTest {
    @Autowired
    private ProducerTemplate template;

    @Test
    public void testServiceCall() throws Exception {
        Assert.assertEquals("9090", template.requestBody("direct:start", null, String.class));
        Assert.assertEquals("9092", template.requestBody("direct:start", null, String.class));
    }
}

