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
package org.apache.camel.itest.tx;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

/**
 * Unit test will look for the spring .xml file with the same class name
 * but postfixed with -config.xml as filename.
 * <p/>
 * We use Spring Testing for unit test, eg we extend AbstractJUnit4SpringContextTests
 * that is a Spring class.
 *
 * @version 
 */
@ContextConfiguration
public class JmsToHttpTXWithRollbackTest extends AbstractJUnit4SpringContextTests {

    // use uri to refer to our mock
    @EndpointInject(uri = "mock:rollback")
    MockEndpoint mock;

    // use the spring id to refer to the endpoint we should send data to
    // notice using this id we can setup the actual endpoint in spring XML
    // and we can even use spring ${ } property in the spring XML
    @EndpointInject(ref = "data")
    private ProducerTemplate template;

    // the ok response to expect
    private String ok  = "<?xml version=\"1.0\"?><reply><status>ok</status></reply>";

    @Test
    public void testSendToTXJmsWithRollback() throws Exception {
        // we assume 2 rollbacks
        mock.expectedMessageCount(2);

        // use requestBody to force a InOut message exchange pattern ( = request/reply)
        // will send and wait for a response
        Object out = template.requestBody("<?xml version=\"1.0\"?><request><status id=\"123\"/></request>");

        // compare response
        assertEquals(ok, out);

        // assert the mock is correct
        mock.assertIsSatisfied();
    }

}