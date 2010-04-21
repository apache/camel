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
package org.apache.camel.itest.jetty;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class JettyJmsShutdownTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    protected CamelContext camelContext;

    @Autowired
    protected ProducerTemplate template;

    @Test
    public void testShutdown() throws Exception {
        Future<String> reply1 = template.asyncRequestBody("http://localhost:9000/test", "World", String.class);
        Future<String> reply2 = template.asyncRequestBody("http://localhost:9000/test", "Camel", String.class);
        Future<String> reply3 = template.asyncRequestBody("http://localhost:9000/test", "Tiger", String.class);

        Thread.sleep(1000);

        // shutdown while in progress
        camelContext.stop();

        Assert.assertEquals("Bye World", reply1.get(5, TimeUnit.SECONDS));
        Assert.assertEquals("Bye Camel", reply2.get(5, TimeUnit.SECONDS));
        Assert.assertEquals("Bye Tiger", reply3.get(5, TimeUnit.SECONDS));
    }

}