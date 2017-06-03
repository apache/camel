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
package org.apache.camel.issues;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * CAMEL-4857 issue test
 */
public class Camel4857UriIssueTest extends ContextTestSupport {

    /**
     * An URI of Camel Beanstalk component consists of a hostname, port and a list
     * of tube names. Tube names are separated by "+" character (which is more or less
     * usually used on the Web to make lists), but every tube name may contain URI special
     * characters like ? or +
     */
    class MyEndpoint extends DefaultEndpoint {
        String uri;
        String remaining;

        MyEndpoint(final String uri, final String remaining) {
            this.uri = uri;
            this.remaining = remaining;
        }

        public Producer createProducer() throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Consumer createConsumer(Processor processor) throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public boolean isSingleton() {
            return true;
        }

        public String getUri() {
            return uri;
        }
    }

    class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
            return new MyEndpoint(uri, remaining);
        }

        @Override
        public boolean useRawUri() {
            // we want the raw uri, so our component can understand the endpoint configuration as it was typed
            return true;
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.addComponent("my", new MyComponent());
    }

    public void testExclamationInUri() {
        // %3F is not an ?, it's part of tube name.
        MyEndpoint endpoint = context.getEndpoint("my:host:11303/tube1+tube%2B+tube%3F", MyEndpoint.class);
        assertNotNull("endpoint", endpoint);
        assertEquals("my:host:11303/tube1+tube%2B+tube%3F", endpoint.getUri());
    }

    public void testPath() {
         // Here a tube name is "tube+" and written in URI as "tube%2B", but it gets
         // normalized, so that an endpoint sees "tube1+tube+"
        MyEndpoint endpoint = context.getEndpoint("my:host:11303/tube1+tube%2B", MyEndpoint.class);
        assertEquals("Path contains several tube names, every tube name may have + or ? characters", "host:11303/tube1+tube%2B", endpoint.remaining);
    }

}
