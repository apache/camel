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
package org.apache.camel.component.cmis;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.util.EndpointHelper;

public class DefaultCMISSessionFacadeFactory implements CMISSessionFacadeFactory {

    @Override
    public CMISSessionFacade create(CMISEndpoint endpoint) throws Exception {
        CMISSessionFacade facade = new CMISSessionFacade(endpoint.getCmsUrl());

        // must use a copy of the properties
        Map<String, Object> copy = new HashMap<>(endpoint.getProperties());
        // which we then set on the newly created facade
        EndpointHelper.setReferenceProperties(endpoint.getCamelContext(), facade, copy);
        EndpointHelper.setProperties(endpoint.getCamelContext(), facade, copy);

        return facade;
    }
}
