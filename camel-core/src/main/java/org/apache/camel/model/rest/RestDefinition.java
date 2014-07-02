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
package org.apache.camel.model.rest;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;

/**
 * Represents an XML &lt;rest/&gt; element
 */
@XmlRootElement(name = "rest")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class RestDefinition {

    private String component;
    private String routeId;
    private List<PathDefinition> paths = new ArrayList<PathDefinition>();

    public String getComponent() {
        return component;
    }

    @XmlAttribute
    public void setComponent(String component) {
        this.component = component;
    }

    public String getRouteId() {
        return routeId;
    }

    @XmlAttribute
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public List<PathDefinition> getPaths() {
        return paths;
    }

    @XmlElementRef
    public void setPaths(List<PathDefinition> paths) {
        this.paths = paths;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    public PathDefinition path(String uri) {
        PathDefinition answer = new PathDefinition();
        getPaths().add(answer);
        answer.setUri(uri);
        return answer;
    }

    /**
     * Transforms this REST definition into a list of {@link org.apache.camel.model.RouteDefinition} which
     * Camel routing engine can add and run. This allows us to define REST services using this
     * REST DSL and turn those into regular Camel routes.
     */
    public List<RouteDefinition> asRouteDefinition(ModelCamelContext camelContext) throws Exception {
        List<RouteDefinition> answer = new ArrayList<RouteDefinition>();

        for (PathDefinition path : getPaths()) {
            String uri = path.getUri();
            for (VerbDefinition verb : path.getVerbs()) {
                String from = "rest-binding:" + verb.getMethod() + ":" + uri + (verb.getUri() != null ? verb.getUri() : "");
                RouteDefinition route = new RouteDefinition();
                route.fromRest(from);
                answer.add(route);
                route.getOutputs().add(verb.getTo());
            }
        }

        return answer;
    }

}
