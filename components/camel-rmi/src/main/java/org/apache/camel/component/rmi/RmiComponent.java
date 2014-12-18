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
package org.apache.camel.component.rmi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * @version 
 */
public class RmiComponent extends UriEndpointComponent {

    public RmiComponent() {
        super(RmiEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RmiEndpoint rmi = new RmiEndpoint(uri, this);

        // lookup remote interfaces
        List<Class<?>> classes = new ArrayList<Class<?>>();
        Iterator<?> it = getAndRemoveParameter(parameters, "remoteInterfaces", Iterator.class);
        while (it != null && it.hasNext()) {
            Object next = it.next();
            Class<?> clazz = getCamelContext().getTypeConverter().mandatoryConvertTo(Class.class, next);
            classes.add(clazz);
        }

        if (!classes.isEmpty()) {
            List<Class<?>> interfaces = classes;
            rmi.setRemoteInterfaces(interfaces);
        }

        setProperties(rmi, parameters);
        return rmi;
    }
}
