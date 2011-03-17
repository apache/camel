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
package org.apache.camel.component.context;

import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supports the explicit and verbose URIs of the form <tt>context:camelContextId:someEndpoint</tt> to access
 * a local endpoint inside an external {@link org.apache.camel.CamelContext}.
 * <p/>
 * Typically there's no need to use this level of verbosity, you can just use <tt>camelContextId:someEndpoint</tt>
 */
public class QualifiedContextComponent extends DefaultComponent {
    private static final transient Logger LOG = LoggerFactory.getLogger(QualifiedContextComponent.class);

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String splitURI[] = ObjectHelper.splitOnCharacter(remaining, ":", 2);
        if (splitURI[1] != null) {
            String contextId = splitURI[0];
            String localEndpoint = splitURI[1];
            Component component = getCamelContext().getComponent(contextId);
            if (component != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Attempting to create local endpoint: " + localEndpoint + " inside the component: " + component);
                }
                return component.createEndpoint(localEndpoint);
            }
        }

        return null;
    }

}
