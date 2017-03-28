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
package org.apache.camel.component.jolt;

import java.util.Map;

import com.bazaarvoice.jolt.Transform;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ResourceHelper;

public class JoltComponent extends UriEndpointComponent {
    @Metadata(label = "advanced")
    private Transform transform;

    public JoltComponent() {
        super(JoltEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, Boolean.TRUE);

        JoltEndpoint answer = new JoltEndpoint(uri, this, remaining);
        answer.setContentCache(cache);
        answer.setTransform(transform);

        // if its a http resource then append any remaining parameters and update the resource uri
        if (ResourceHelper.isHttpUri(remaining)) {
            remaining = ResourceHelper.appendParameters(remaining, parameters);
            answer.setResourceUri(remaining);
        }

        return answer;
    }

    public Transform getTransform() {
        return transform;
    }

    /**
     * Explicitly sets the Transform to use. If not set a Transform specified by the transformDsl will be created
     */
    public void setTransform(Transform transform) {
        this.transform = transform;
    }


}
