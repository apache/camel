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
package org.apache.camel.spring.placeholder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.awaitility.Awaitility.await;

@ContextConfiguration
public class SimpleLanguageWithSpringPropertyPlaceholderRouteTest extends SpringRunWithTestSupport {
    
    @Produce("direct:startSimple")
    protected ProducerTemplate template;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/outBox");
        deleteDirectory("target/outBoxSimple");
    }

    @Test
    @DirtiesContext
    public void replaceSimpleExpression() throws Exception {
        template.sendBody("Test");

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertDirectoryExists("target/outBoxSimple/"));
    }
    
    @Ignore(value = "disabled because of https://jira.springsource.org/browse/SPR-7593")
    @Test
    @DirtiesContext
    public void replaceExpression() throws Exception {
        template.sendBody("direct:start", "Test");

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertFileExists("target/outBox/" + getTestFileName()));
    }

    private String getTestFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String s = sdf.format(new Date());
        return "test-" + s + ".txt";
    }
    
}
