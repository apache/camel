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
package org.apache.camel.component.uface;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.core.databinding.observable.Realm;

/**
 * @version $Revision$
 */
public class UFaceTest extends ContextTestSupport {
    // lets install a Realm to avoid null pointer exceptions
    private Realm realm = new TestRealm();

    public void testUFaceEndpoints() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedMessageCount(1);

        template.sendBody("uface:a", "<hello>world!</hello>");

        assertMockEndpointsSatisifed();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("uface:a").to("uface:b").to("mock:results");
            }
        };
    }

    protected static class TestRealm extends Realm {
        public TestRealm() {
            Realm.setDefault(this);
        }

        public boolean isCurrent() {
            return true;
        }
    }
}
