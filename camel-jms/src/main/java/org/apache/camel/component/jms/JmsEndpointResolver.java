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
package org.apache.camel.component.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.EndpointResolver;
import org.apache.camel.util.ObjectHelper;

import java.util.concurrent.Callable;

/**
 * An implementation of {@link EndpointResolver} that creates
 * {@link JmsEndpoint} objects.
 * <p/>
 * The syntax for a JMS URI looks like:
 * <p/>
 * <pre><code>jms:[component:]destination</code></pre>
 * the component is optional, and if it is not specified, the default component name
 * is assumed.
 *
 * @version $Revision:520964 $
 */
public class JmsEndpointResolver implements EndpointResolver<JmsExchange> {
    public static final String DEFAULT_COMPONENT_NAME = JmsEndpointResolver.class.getName();

    /**
     * Finds the {@see JmsComponent} specified by the uri.  If the {@see JmsComponent}
     * object do not exist, it will be created.
     */
    public Component resolveComponent(CamelContext container, String uri) {
        String id[] = getEndpointId(uri);
        return resolveJmsComponent(container, id[0]);
    }

    /**
     * Finds the {@see QueueEndpoint} specified by the uri.  If the {@see QueueEndpoint} or it's associated
     * {@see QueueComponent} object do not exist, they will be created.
     */
    public JmsEndpoint resolveEndpoint(CamelContext container, String uri) {
        String id[] = getEndpointId(uri);
        JmsComponent component = resolveJmsComponent(container, id[0]);
        return component.createEndpoint(uri, id[1]);
    }

    /**
     * @return an array that looks like: [componentName,endpointName]
     */
    private String[] getEndpointId(String uri) {
        String rc[] = {DEFAULT_COMPONENT_NAME, null};
        String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 3);
        if (splitURI[2] != null) {
            rc[0] = splitURI[1];
            rc[1] = splitURI[2];
        }
        else {
            rc[1] = splitURI[1];
        }
        return rc;
    }

    @SuppressWarnings("unchecked")
    private JmsComponent resolveJmsComponent(final CamelContext container, final String componentName) {
        Component rc = container.getOrCreateComponent(componentName, new Callable() {
            public JmsComponent call() throws Exception {
                return new JmsComponent(container);
            }
        });
        return (JmsComponent) rc;
    }
}
