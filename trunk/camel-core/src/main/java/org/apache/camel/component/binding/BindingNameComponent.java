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
package org.apache.camel.component.binding;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Binding;
import org.apache.camel.util.CamelContextHelper;

import static org.apache.camel.util.CamelContextHelper.getMandatoryEndpoint;

/**
 * A Binding component using the URI form <code>binding:nameOfBinding:endpointURI</code>
 * to extract the binding name which is then resolved from the registry and used to create a
 * {@link BindingEndpoint} from the underlying {@link Endpoint}
 */
public class BindingNameComponent extends DefaultComponent {
    protected static final String BAD_FORMAT_MESSAGE = "URI should be of the format binding:nameOfBinding:endpointURI";

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        CamelContext camelContext = getCamelContext();
        int idx = remaining.indexOf(":");
        if (idx <= 0) {
            throw new IllegalArgumentException(BAD_FORMAT_MESSAGE);
        }
        String bindingName = remaining.substring(0, idx);
        String delegateURI = remaining.substring(idx + 1);
        if (delegateURI.isEmpty()) {
            throw new IllegalArgumentException(BAD_FORMAT_MESSAGE);
        }
        Binding binding = CamelContextHelper.mandatoryLookup(camelContext, bindingName, Binding.class);
        Endpoint delegate = getMandatoryEndpoint(camelContext, delegateURI);
        return new BindingEndpoint(uri, this, binding,  delegate);
    }
}
