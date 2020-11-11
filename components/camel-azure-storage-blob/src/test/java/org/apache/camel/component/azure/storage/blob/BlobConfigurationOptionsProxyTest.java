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
package org.apache.camel.component.azure.storage.blob;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlobConfigurationOptionsProxyTest extends CamelTestSupport {

    @Test
    void testIfCorrectOptionsReturnedCorrectly() {
        final BlobConfiguration configuration = new BlobConfiguration();

        // first case: when exchange is set
        final Exchange exchange = new DefaultExchange(context);
        final BlobConfigurationOptionsProxy configurationOptionsProxy = new BlobConfigurationOptionsProxy(configuration);

        exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "testBlobExchange");
        configuration.setBlobName("testBlobConfig");

        assertEquals("testBlobExchange", configurationOptionsProxy.getBlobName(exchange));

        // second class: exchange is empty
        exchange.getIn().setHeader(BlobConstants.BLOB_NAME, null);

        assertEquals("testBlobConfig", configurationOptionsProxy.getBlobName(exchange));

        // third class: if no option at all
        configuration.setBlobName(null);

        assertNull(configurationOptionsProxy.getBlobName(exchange));
    }

    @Test
    void testIfCorrectOptionsReturnedCorrectlyWithPrefixAndRegexSet() {
        final BlobConfiguration configuration = new BlobConfiguration();

        final Exchange exchange = new DefaultExchange(context);
        final BlobConfigurationOptionsProxy configurationOptionsProxy = new BlobConfigurationOptionsProxy(configuration);

        configuration.setPrefix("test");
        configuration.setRegex(".*\\.exe");

        assertNull(configurationOptionsProxy.getPrefix(exchange));
        assertEquals(".*\\.exe", configurationOptionsProxy.getRegex(exchange));
    }

    @Test
    void testIfCorrectOptionsReturnedCorrectlyWithPrefixAndRegexSetInHeader() {
        final BlobConfiguration configuration = new BlobConfiguration();

        // first case: when exchange is set
        final Exchange exchange = new DefaultExchange(context);
        final BlobConfigurationOptionsProxy configurationOptionsProxy = new BlobConfigurationOptionsProxy(configuration);

        configuration.setPrefix("test");
        configuration.setRegex(".*\\.exe");
        exchange.getIn().setHeader(BlobConstants.PREFIX, "test2");
        exchange.getIn().setHeader(BlobConstants.REGEX, ".*\\.pdf");
        assertNull(configurationOptionsProxy.getPrefix(exchange));
        assertEquals(".*\\.pdf", configurationOptionsProxy.getRegex(exchange));
    }

    @Test
    void testIfCorrectOptionsReturnedCorrectlyWithPrefixSet() {
        final BlobConfiguration configuration = new BlobConfiguration();

        // first case: when exchange is set
        final Exchange exchange = new DefaultExchange(context);
        final BlobConfigurationOptionsProxy configurationOptionsProxy = new BlobConfigurationOptionsProxy(configuration);

        configuration.setPrefix("test");
        assertEquals("test", configurationOptionsProxy.getPrefix(exchange));

        //test header override
        exchange.getIn().setHeader(BlobConstants.PREFIX, "test2");
        assertEquals("test2", configurationOptionsProxy.getPrefix(exchange));
    }

    @Test
    void testIfCorrectOptionsReturnedCorrectlyWithRegexSet() {
        final BlobConfiguration configuration = new BlobConfiguration();

        // first case: when exchange is set
        final Exchange exchange = new DefaultExchange(context);
        final BlobConfigurationOptionsProxy configurationOptionsProxy = new BlobConfigurationOptionsProxy(configuration);

        configuration.setRegex(".*\\.exe");
        assertEquals(".*\\.exe", configurationOptionsProxy.getRegex(exchange));

        //test header override
        exchange.getIn().setHeader(BlobConstants.REGEX, ".*\\.pdf");
        assertEquals(".*\\.pdf", configurationOptionsProxy.getRegex(exchange));
    }
}
