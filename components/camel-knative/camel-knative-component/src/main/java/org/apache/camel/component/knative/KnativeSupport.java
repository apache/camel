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
package org.apache.camel.component.knative;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.camel.CamelContext;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.knative.spi.KnativeResource;
import org.apache.camel.component.knative.spi.KnativeSinkBinding;
import org.apache.camel.util.ObjectHelper;

public class KnativeSupport {
    private KnativeSupport() {

    }

    public static KnativeResource asResource(CamelContext camelContext, KnativeSinkBinding binding) {
        final String kSinkUrl = camelContext.resolvePropertyPlaceholders("{{k.sink:}}");
        final String kCeOverride = camelContext.resolvePropertyPlaceholders("{{k.ce.overrides:}}");

        // create a synthetic service definition to target the K_SINK url
        KnativeResource resource = new KnativeResource();
        resource.setEndpointKind(Knative.EndpointKind.sink);
        resource.setType(binding.getType());
        resource.setName(binding.getName());
        resource.setObjectApiVersion(binding.getObjectApiVersion());
        resource.setObjectKind(binding.getObjectKind());

        if (ObjectHelper.isNotEmpty(kSinkUrl)) {
            resource.setUrl(kSinkUrl);
        }

        if (binding.getType() == Knative.Type.event) {
            resource.setObjectName(binding.getName());
        }

        if (ObjectHelper.isNotEmpty(kCeOverride)) {
            try (Reader reader = new StringReader(kCeOverride)) {
                // assume K_CE_OVERRIDES is defined as simple key/val json
                Knative.MAPPER.readValue(
                        reader,
                        new TypeReference<HashMap<String, String>>() {
                        }).forEach(resource::addCeOverride);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return resource;
    }
}
