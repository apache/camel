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
package org.apache.camel.component.grape

import org.apache.camel.CamelContext
import org.apache.camel.impl.UriEndpointComponent

class GrapeComponent extends UriEndpointComponent {

    private PatchesRepository patchesRepository = new FilePatchesRepository()

    GrapeComponent() {
        super(GrapeEndpoint.class)
    }

    @Override
    protected GrapeEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
        GrapeEndpoint grapeEndpoint = new GrapeEndpoint(uri, remaining, this)
        setProperties(grapeEndpoint, parameters);
        grapeEndpoint
    }

    PatchesRepository getPatchesRepository() {
        return patchesRepository
    }

    void setPatchesRepository(PatchesRepository patchesRepository) {
        this.patchesRepository = patchesRepository
    }

    static CamelContext grapeCamelContext(CamelContext camelContext) {
        camelContext.setApplicationContextClassLoader(new GroovyClassLoader(GrapeComponent.class.getClassLoader()))
        camelContext
    }

}
