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

package org.apache.camel.component.file.remote;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Test to ensure the FtpEndpoint URI is sanitized.
 */
public class FtpEndpointURISanitizedTest extends FtpServerTestSupport {

    private String password = "secret";

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/////foo?password=" + password + "&delay=5000";
    }

    @Test
    public void testFtpDirectoryRelative() throws Exception {
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        assertThat(((FtpEndpoint<?>) endpoint).getConfiguration().getDirectoryName(), equalTo("foo"));
    }

    @Test
    public void testFtpConsumerUriSanitized() throws Exception {
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        Consumer consumer = endpoint.createConsumer(null);
        assertFalse(consumer.toString().contains(password));
    }

    @Test
    public void testFtpProducerUriSanitized() throws Exception {
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        Producer producer = endpoint.createProducer();
        assertFalse(producer.toString().contains(password));
    }
}
