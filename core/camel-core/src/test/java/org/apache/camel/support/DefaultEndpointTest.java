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
package org.apache.camel.support;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.util.URISupport;
import org.junit.Test;

public class DefaultEndpointTest extends ContextTestSupport {

    @Test
    public void testSanitizeUri() {
        assertNull(URISupport.sanitizeUri(null));
        assertEquals("", URISupport.sanitizeUri(""));
        assertSanitizedUriUnchanged("http://camel.apache.org");
        assertSanitizedUriUnchanged("irc://irc.codehaus.org/camel");
        assertSanitizedUriUnchanged("direct:foo?bar=123&cheese=yes");
        assertSanitizedUriUnchanged("https://issues.apache.org/activemq/secure/AddComment!default.jspa?id=33239");
        assertEquals("ftp://host.mysite.com/records?passiveMode=true&user=someuser&password=xxxxxx",
                     URISupport.sanitizeUri("ftp://host.mysite.com/records?passiveMode=true&user=someuser&password=superSecret"));
        assertEquals("sftp://host.mysite.com/records?user=someuser&privateKeyFile=key.file&privateKeyFilePassphrase=xxxxxx&knownHostsFile=hosts.list",
                     URISupport.sanitizeUri("sftp://host.mysite.com/records?user=someuser&privateKeyFile=key.file&privateKeyFilePassphrase=superSecret&knownHostsFile=hosts.list"));
        assertEquals("aws-sqs://MyQueue?accessKey=1672t4rflhnhli3&secretKey=xxxxxx",
                     URISupport.sanitizeUri("aws-sqs://MyQueue?accessKey=1672t4rflhnhli3&secretKey=qi472qfberu33dqjncq"));
    }

    @Test
    public void testToString() {
        final String epstr = "myep:///test";
        MyEndpoint ep = new MyEndpoint();
        ep.setEndpointUri(epstr);
        assertTrue(ep.toString().contains(epstr));
    }

    /**
     * Ensures that the Uri was not changed because no password was found.
     *
     * @param uri The uri to test.
     */
    private void assertSanitizedUriUnchanged(String uri) {
        assertEquals(uri, URISupport.sanitizeUri(uri));
    }

    private static class MyEndpoint extends DefaultEndpoint {
        @Override
        public Producer createProducer() throws Exception {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return false;
        }
    }
}
