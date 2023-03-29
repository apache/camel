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
package org.apache.camel.openapi;

import java.util.Collections;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.model.Model;
import org.apache.camel.model.rest.RestDefinition;

public class DefaultRestDefinitionsResolver implements RestDefinitionsResolver {

    @Override
    public List<RestDefinition> getRestDefinitions(CamelContext camelContext, String camelId) throws Exception {
        if (camelId != null && !camelContext.getName().equals(camelId)) {
            throw new UnsupportedOperationException(
                    "Must use JmxRestDefinitionsResolver to generate rest model from another CamelContext in the same JVM");
        }

        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        List<RestDefinition> rests = model.getRestDefinitions();
        if (rests.isEmpty()) {
            return null;
        }
        return rests;
    }

    @Override
    public List<String> findCamelContexts() throws Exception {
        return Collections.emptyList();
    }

}
