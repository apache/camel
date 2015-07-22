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
package org.apache.camel.component.elasticsearch;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;

import org.junit.Test;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringElasticsearchTest extends CamelSpringTestSupport {

    @Produce(uri = "direct:index")
    protected ProducerTemplate producer;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mock;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        deleteDirectory("target/data");
        return new ClassPathXmlApplicationContext("org/apache/camel/component/elasticsearch/SpringElasticsearchTest-context.xml");
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // let's speed up the tests using the same context
        return true;
    }

    @Test
    public void testSendBody() throws Exception {
        mock.expectedMinimumMessageCount(1);

        Map<String, String> body = new HashMap<String, String>();
        body.put("content", "test");
        producer.sendBody(body);

        mock.assertIsSatisfied();
    }

    @Test
    public void testSendBodyAndHeaders() throws Exception {
        mock.expectedMinimumMessageCount(1);

        Map<String, String> body = new HashMap<String, String>();
        body.put("content", "test");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        producer.sendBodyAndHeaders(body, headers);

        mock.assertIsSatisfied();
    }
}
