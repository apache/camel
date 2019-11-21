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
package org.apache.camel.component.aws.s3;

import com.amazonaws.services.s3.model.ObjectListing;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class S3ComponentListObjectsSpringTest extends CamelSpringTestSupport {

    @EndpointInject("direct:listBuckets")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:listObjects", ExchangePattern.InOnly, "");
        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0));

    }

    private void assertResultExchange(Exchange resultExchange) {
        ObjectListing list = resultExchange.getIn().getBody(ObjectListing.class);
        assertEquals(1, list.getObjectSummaries().size());
        assertEquals("Myfile", list.getObjectSummaries().get(0).getKey());
        assertEquals("test", list.getObjectSummaries().get(0).getBucketName());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws/s3/S3ComponentSpringTest-context.xml");
    }
}
