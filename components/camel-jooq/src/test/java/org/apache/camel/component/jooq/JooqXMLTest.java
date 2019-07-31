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
package org.apache.camel.component.jooq;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.jooq.UpdatableRecord;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Only for testing XML DSL. All basic tests are located here: {@link JooqProducerTest}, {@link JooqConsumerTest}.
 */
@ContextConfiguration(locations = {"/jooq-spring.xml", "/camel-context.xml"})
public class JooqXMLTest extends BaseJooqTest {

    @Autowired
    CamelContext context;

    @Test
    public void testInsert() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        UpdatableRecord entity = (UpdatableRecord)producerTemplate.sendBody(context.getEndpoint("direct:insert"), ExchangePattern.InOut, "empty");
        Assert.assertNotNull(entity);
    }

    @Test
    public void testExecute() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody(context.getEndpoint("direct:execute"), ExchangePattern.InOut, "empty");
    }

    @Test
    public void testFetch() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody(context.getEndpoint("direct:fetch"), ExchangePattern.InOut, "empty");
    }

    @Test
    public void testSQLSelect() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody(context.getEndpoint("direct:sql-select"), ExchangePattern.InOut, "empty");
    }

    @Test
    public void testSQLDelete() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody(context.getEndpoint("direct:sql-delete"), ExchangePattern.InOut, "empty");
    }
}
