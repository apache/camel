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
package org.apache.camel.component.fop;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link FopEndpoint}.
 */
public class FopComponent extends UriEndpointComponent {

    public FopComponent() {
        super(FopEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        FopOutputType type = FopOutputType.asFooOutputType(remaining);
        if (type == null) {
            type = getCamelContext().getTypeConverter().mandatoryConvertTo(FopOutputType.class, remaining);
        }
        FopEndpoint endpoint = new FopEndpoint(uri, this, type);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
