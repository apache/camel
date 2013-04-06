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
package org.apache.camel.web.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * Represents the list of the currently active <a href="http://camel.apache.org/component.html">components</a>
 * in the current camel context
 */
public class ComponentsResource extends CamelChildResourceSupport {
    public ComponentsResource(CamelContextResource contextResource) {
        super(contextResource);
    }

    public List<String> getComponentIds() {
        List<String> answer = new ArrayList<String>(getCamelContext().getComponentNames());
        Collections.sort(answer);
        return answer;
    }

    /**
     * Returns a specific component
     */
    @Path("{id}")
    public ComponentResource getLanguage(@PathParam("id") String id) {
        if (id == null) {
            return null;
        }
        return new ComponentResource(getContextResource(), id);
    }
}