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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.util.URISupport;

/**
 * Represents an XML &lt;rest/&gt; element
 */
@XmlRootElement(name = "rest")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestDefinition {

    @XmlAttribute
    private String uri;
    @XmlAttribute
    private String component;
    @XmlElementRef
    private List<VerbDefinition> verbs = new ArrayList<VerbDefinition>();

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public List<VerbDefinition> getVerbs() {
        return verbs;
    }

    public void setVerbs(List<VerbDefinition> verbs) {
        this.verbs = verbs;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * To use a specific Camel rest component
     */
    public RestDefinition component(String componentId) {
        setComponent(componentId);
        return this;
    }

    public RestDefinition get() {
        return addVerb("get", null);
    }

    public RestDefinition post() {
        return addVerb("post", null);
    }

    public RestDefinition put() {
        return addVerb("put", null);
    }

    public RestDefinition delete() {
        return addVerb("delete", null);
    }

    public RestDefinition head() {
        return addVerb("head", null);
    }

    public RestDefinition verb(String verb) {
        return addVerb(verb, null);
    }

    public RestDefinition consumes(String accept) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        verb.setConsumes(accept);

        return this;
    }

    @Deprecated
    public VerbDefinition to(String url) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        verb.addOutput(new ToDefinition(url));

        return verb;
    }

    // Implementation
    //-------------------------------------------------------------------------

    private RestDefinition addVerb(String verb, String url) {
        VerbDefinition answer;

        if ("get".equals(verb)) {
            answer = new GetVerbDefinition();
        } else if ("post".equals(verb)) {
            answer = new PostVerbDefinition();
        } else if ("delete".equals(verb)) {
            answer = new DeleteVerbDefinition();
        } else if ("head".equals(verb)) {
            answer = new HeadVerbDefinition();
        } else if ("put".equals(verb)) {
            answer = new PutVerbDefinition();
        } else {
            answer = new VerbDefinition();
            answer.setMethod(verb);
        }

        answer.setRest(this);
        getVerbs().add(answer);
        return this;
    }

    /**
     * Transforms this REST definition into a list of {@link org.apache.camel.model.RouteDefinition} which
     * Camel routing engine can add and run. This allows us to define REST services using this
     * REST DSL and turn those into regular Camel routes.
     */
    public List<RouteDefinition> asRouteDefinition(ModelCamelContext camelContext) throws Exception {
        List<RouteDefinition> answer = new ArrayList<RouteDefinition>();

        for (VerbDefinition verb : getVerbs()) {
            String from = "rest:" + verb.asVerb() + ":" + uri;
            // append options
            Map<String, Object> options = new HashMap<String, Object>();
            if (getComponent() != null) {
                options.put("componentName", getComponent());
            }
            if (verb.getConsumes() != null) {
                options.put("consumes", verb.getConsumes());
            }
            if (!options.isEmpty()) {
                String query = URISupport.createQueryString(options);
                from = from + "?" + query;
            }

            RouteDefinition route = new RouteDefinition();
            route.fromRest(from);
            answer.add(route);
            route.getOutputs().addAll(verb.getOutputs());
        }

        return answer;
    }

}
