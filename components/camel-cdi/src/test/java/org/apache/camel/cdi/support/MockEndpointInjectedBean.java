/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.cdi.support;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.cdi.Mock;
import org.apache.camel.component.mock.MockEndpoint;

import javax.inject.Inject;
import javax.inject.Named;

public class MockEndpointInjectedBean {

    /*

    TODO - cannot currently figure out how to be able to inject both Endpoint and MockEndpoint
    using a @Produces plugin with a single method without using explicit qualifier annotations
    to separate the two scenarios which is a bit ugly.

    See discussion here:
    https://issues.apache.org/jira/browse/CAMEL-5553

    Ideally it would be nice to be able to do this:

    @Inject
    @EndpointInject(uri = "mock:blah")
    private MockEndpoint endpoint;

    */

    @Inject @Mock
    private MockEndpoint foo;

    @Inject @Mock @EndpointInject(uri = "mock:something")
    private MockEndpoint bar;

    public MockEndpoint getBar() {
        return bar;
    }

    public MockEndpoint getFoo() {
        return foo;
    }
}
