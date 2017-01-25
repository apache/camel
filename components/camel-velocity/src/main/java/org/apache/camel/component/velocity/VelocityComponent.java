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
package org.apache.camel.component.velocity;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ResourceHelper;
import org.apache.velocity.app.VelocityEngine;

/**
 * @version 
 */
public class VelocityComponent extends UriEndpointComponent {

    @Metadata(label = "advanced")
    private VelocityEngine velocityEngine;
    
    public VelocityComponent() {
        super(VelocityEndpoint.class);
    }

    public VelocityEngine getVelocityEngine() {
        return velocityEngine;
    }

    /**
     * To use the {@link VelocityEngine} otherwise a new engine is created
     */
    public void setVelocityEngine(VelocityEngine velocityEngine) {
        this.velocityEngine = velocityEngine;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, Boolean.TRUE);

        VelocityEndpoint answer = new VelocityEndpoint(uri, this, remaining);
        setProperties(answer, parameters);
        answer.setContentCache(cache);
        answer.setVelocityEngine(velocityEngine);

        // if its a http resource then append any remaining parameters and update the resource uri
        if (ResourceHelper.isHttpUri(remaining)) {
            remaining = ResourceHelper.appendParameters(remaining, parameters);
            answer.setResourceUri(remaining);
        }

        return answer;
    }
}
