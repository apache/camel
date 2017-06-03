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
package org.apache.camel.component.test;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.URISupport;

/**
 * The <a href="http://camel.apache.org/test.html">Test Component</a> is for simplifying unit and integration tests.
 *
 * Component for testing by polling test messages from another endpoint on startup as the expected message bodies to
 * receive during testing.
 *
 * @version 
 */
public class TestComponent extends UriEndpointComponent {

    public TestComponent() {
        super(TestEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Long timeout = getAndRemoveParameter(parameters, "timeout", Long.class);
        TestEndpoint answer = new TestEndpoint(uri, this);
        if (timeout != null) {
            answer.setTimeout(timeout);
        }
        setProperties(answer, parameters);

        // from the rest create a new uri with those parameters
        String endpointUri = URISupport.appendParametersToURI(remaining, parameters);
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(getCamelContext(), endpointUri);
        answer.setExpectedMessageEndpoint(endpoint);
        return answer;
    }

}