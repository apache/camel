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
package org.apache.camel.management;

import org.apache.camel.Component;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * CustomEndpoint is used to test {@link org.apache.camel.management.JmxInstrumentationCustomMBeanTest}
 * and must be declared a public class otherwise the mbean server connection cannot access its methods.
 */
// START SNIPPET: e1
@ManagedResource(description = "Our custom managed endpoint")
public class CustomEndpoint extends MockEndpoint {

    public CustomEndpoint(final String endpointUri, final Component component) {
        super(endpointUri, component);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected String createEndpointUri() {
        return "custom";
    }

    @ManagedAttribute
    public String getFoo() {
        return "bar";
    }

    @Override
    @ManagedAttribute
    public String getEndpointUri() {
        return super.getEndpointUri();
    }
}
// END SNIPPET: e1
