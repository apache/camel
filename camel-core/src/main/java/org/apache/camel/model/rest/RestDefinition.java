/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model.rest;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Defines a rest service using the rest-dsl
 */
@Metadata(label = "rest")
@XmlRootElement(name = "rest")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestDefinition extends OptionalIdentifiedDefinition<RestDefinition> {

    @XmlAttribute
    private String path;

    @XmlAttribute
    private String consumes;

    @XmlAttribute
    private String produces;

    @XmlAttribute
    @Metadata(defaultValue = "auto")
    private RestBindingMode bindingMode;

    @XmlAttribute
    private Boolean skipBindingOnErrorCode;

    @XmlAttribute
    private Boolean enableCORS;

    @XmlElementRef
    private List<VerbDefinition> verbs = new ArrayList<VerbDefinition>();

    @Override
    public String getLabel() {
        return "rest";
    }

    public String getPath() {
        return path;
    }

    /**
     * Path of the rest service, such as "/foo"
     */
    public void setPath(String path) {
        this.path = path;
    }

    public String getConsumes() {
        return consumes;
    }

    /**
     * To define the content type what the REST service consumes (accept as input), such as application/xml or application/json.
     * This option will override what may be configured on a parent level
     */
    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    /**
     * To define the content type what the REST service produces (uses for output), such as application/xml or application/json
     * This option will override what may be configured on a parent level
     */
    public void setProduces(String produces) {
        this.produces = produces;
    }

    public RestBindingMode getBindingMode() {
        return bindingMode;
    }

    /**
     * Sets the binding mode to use.
     * This option will override what may be configured on a parent level
     * <p/>
     * The default value is auto
     */
    public void setBindingMode(RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public List<VerbDefinition> getVerbs() {
        return verbs;
    }

    /**
     * The HTTP verbs this REST service accepts and uses
     */
    public void setVerbs(List<VerbDefinition> verbs) {
        this.verbs = verbs;
    }

    public Boolean getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    /**
     * Whether to skip binding on output if there is a custom HTTP error code header.
     * This allows to build custom error messages that do not bind to json / xml etc, as success messages otherwise will do.
     * This option will override what may be configured on a parent level
     */
    public void setSkipBindingOnErrorCode(Boolean skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public Boolean getEnableCORS() {
        return enableCORS;
    }

    /**
     * Whether to enable CORS headers in the HTTP response.
     * This option will override what may be configured on a parent level
     * <p/>
     * The default value is false.
     */
    public void setEnableCORS(Boolean enableCORS) {
        this.enableCORS = enableCORS;
    }

    // Fluent API
    //-------------------------------------------------------------------------


    /**
     * To set the base path of this REST service
     */
    public RestDefinition path(String path) {
        setPath(path);
        return this;
    }

    public RestDefinition get() {
        return addVerb("get", null);
    }

    public RestDefinition get(String uri) {
        return addVerb("get", uri);
    }

    public RestDefinition post() {
        return addVerb("post", null);
    }

    public RestDefinition post(String uri) {
        return addVerb("post", uri);
    }

    public RestDefinition put() {
        return addVerb("put", null);
    }

    public RestDefinition put(String uri) {
        return addVerb("put", uri);
    }

    public RestDefinition delete() {
        return addVerb("delete", null);
    }

    public RestDefinition delete(String uri) {
        return addVerb("delete", uri);
    }

    public RestDefinition head() {
        return addVerb("head", null);
    }

    public RestDefinition head(String uri) {
        return addVerb("head", uri);
    }

    public RestDefinition verb(String verb) {
        return addVerb(verb, null);
    }

    public RestDefinition verb(String verb, String uri) {
        return addVerb(verb, uri);
    }

    @Override
    public RestDefinition id(String id) {
        if (getVerbs().isEmpty()) {
            super.id(id);
        } else {
            // add on last verb as that is how the Java DSL works
            VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
            verb.id(id);
        }

        return this;
    }

    @Override
    public RestDefinition description(String text) {
        if (getVerbs().isEmpty()) {
            super.description(text);
        } else {
            // add on last verb as that is how the Java DSL works
            VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
            verb.description(text);
        }

        return this;
    }

    @Override
    public RestDefinition description(String id, String text, String lang) {
        if (getVerbs().isEmpty()) {
            super.description(id, text, lang);
        } else {
            // add on last verb as that is how the Java DSL works
            VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
            verb.description(id, text, lang);
        }

        return this;
    }

    public RestDefinition consumes(String mediaType) {
        if (getVerbs().isEmpty()) {
            this.consumes = mediaType;
        } else {
            // add on last verb as that is how the Java DSL works
            VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
            verb.setConsumes(mediaType);
        }

        return this;
    }

    public RestParamDefinition restParam() {
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }
        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        return new RestParamDefinition(verb);
    }

    public RestDefinition produces(String mediaType) {
        if (getVerbs().isEmpty()) {
            this.produces = mediaType;
        } else {
            // add on last verb as that is how the Java DSL works
            VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
            verb.setProduces(mediaType);
        }

        return this;
    }

    public RestDefinition type(Class<?> classType) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        verb.setType(classType.getCanonicalName());
        return this;
    }

    public RestDefinition typeList(Class<?> classType) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        // list should end with [] to indicate array
        verb.setType(classType.getCanonicalName() + "[]");
        return this;
    }

    public RestDefinition outType(Class<?> classType) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        verb.setOutType(classType.getCanonicalName());
        return this;
    }

    public RestDefinition outTypeList(Class<?> classType) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        // list should end with [] to indicate array
        verb.setOutType(classType.getCanonicalName() + "[]");
        return this;
    }

    public RestDefinition bindingMode(RestBindingMode mode) {
        if (getVerbs().isEmpty()) {
            this.bindingMode = mode;
        } else {
            // add on last verb as that is how the Java DSL works
            VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
            verb.setBindingMode(mode);
        }

        return this;
    }

    public RestDefinition skipBindingOnErrorCode(boolean skipBindingOnErrorCode) {
        if (getVerbs().isEmpty()) {
            this.skipBindingOnErrorCode = skipBindingOnErrorCode;
        } else {
            // add on last verb as that is how the Java DSL works
            VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
            verb.setSkipBindingOnErrorCode(skipBindingOnErrorCode);
        }

        return this;
    }

    public RestDefinition enableCORS(boolean enableCORS) {
        if (getVerbs().isEmpty()) {
            this.enableCORS = enableCORS;
        } else {
            // add on last verb as that is how the Java DSL works
            VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
            verb.setEnableCORS(enableCORS);
        }

        return this;
    }

    /**
     * Routes directly to the given endpoint.
     * <p/>
     * If you need additional routing capabilities, then use {@link #route()} instead.
     *
     * @param uri the uri of the endpoint
     * @return this builder
     */
    public RestDefinition to(String uri) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        ToDefinition to = new ToDefinition(uri);

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        verb.setTo(to);
        return this;
    }

    public RouteDefinition route() {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        // link them together so we can navigate using Java DSL
        RouteDefinition route = new RouteDefinition();
        route.setRestDefinition(this);
        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        verb.setRoute(route);
        return route;
    }

    // Implementation
    //-------------------------------------------------------------------------

    private RestDefinition addVerb(String verb, String uri) {
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
        getVerbs().add(answer);
        answer.setRest(this);
        answer.setUri(uri);
        return this;
    }

    /**
     * Transforms this REST definition into a list of {@link org.apache.camel.model.RouteDefinition} which
     * Camel routing engine can add and run. This allows us to define REST services using this
     * REST DSL and turn those into regular Camel routes.
     */
    public List<RouteDefinition> asRouteDefinition(CamelContext camelContext) {
        List<RouteDefinition> answer = new ArrayList<RouteDefinition>();

        for (VerbDefinition verb : getVerbs()) {
            // either the verb has a singular to or a embedded route
            RouteDefinition route = verb.getRoute();
            if (route == null) {
                // it was a singular to, so add a new route and add the singular
                // to as output to this route
                route = new RouteDefinition();
                route.getOutputs().add(verb.getTo());
            }

            // add the binding
            RestBindingDefinition binding = new RestBindingDefinition();
            binding.setType(verb.getType());
            binding.setOutType(verb.getOutType());
            // verb takes precedence over configuration on rest
            if (verb.getConsumes() != null) {
                binding.setConsumes(verb.getConsumes());
            } else {
                binding.setConsumes(getConsumes());
            }
            if (verb.getProduces() != null) {
                binding.setProduces(verb.getProduces());
            } else {
                binding.setProduces(getProduces());
            }
            if (verb.getBindingMode() != null) {
                binding.setBindingMode(verb.getBindingMode());
            } else {
                binding.setBindingMode(getBindingMode());
            }
            if (verb.getSkipBindingOnErrorCode() != null) {
                binding.setSkipBindingOnErrorCode(verb.getSkipBindingOnErrorCode());
            } else {
                binding.setSkipBindingOnErrorCode(getSkipBindingOnErrorCode());
            }
            if (verb.getEnableCORS() != null) {
                binding.setEnableCORS(verb.getEnableCORS());
            } else {
                binding.setEnableCORS(getEnableCORS());
            }
            route.getOutputs().add(0, binding);

            // create the from endpoint uri which is using the rest component
            String from = "rest:" + verb.asVerb() + ":" + buildUri(verb);

            // append options
            Map<String, Object> options = new HashMap<String, Object>();
            // verb takes precedence over configuration on rest
            if (verb.getConsumes() != null) {
                options.put("consumes", verb.getConsumes());
            } else if (getConsumes() != null) {
                options.put("consumes", getConsumes());
            }
            if (verb.getProduces() != null) {
                options.put("produces", verb.getProduces());
            } else if (getProduces() != null) {
                options.put("produces", getProduces());
            }

            // append optional type binding information
            String inType = binding.getType();
            if (inType != null) {
                options.put("inType", inType);
            }
            String outType = binding.getOutType();
            if (outType != null) {
                options.put("outType", outType);
            }
            // if no route id has been set, then use the verb id as route id
            if (!route.hasCustomIdAssigned()) {
                // use id of verb as route id
                String id = verb.getId();
                if (id != null) {
                    route.setId(id);
                }
            }
            String routeId = route.idOrCreate(camelContext.getNodeIdFactory());
            options.put("routeId", routeId);

            // include optional description, which we favor from 1) to/route description 2) verb description 3) rest description
            // this allows end users to define general descriptions and override then per to/route or verb
            String description = verb.getTo() != null ? verb.getTo().getDescriptionText() : route.getDescriptionText();
            if (description == null) {
                description = verb.getDescriptionText();
            }
            if (description == null) {
                description = getDescriptionText();
            }
            if (description != null) {
                options.put("description", description);
            }

            if (!options.isEmpty()) {
                String query;
                try {
                    query = URISupport.createQueryString(options);
                } catch (URISyntaxException e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
                from = from + "?" + query;
            }

            // the route should be from this rest endpoint
            route.fromRest(from);
            route.setRestDefinition(this);
            answer.add(route);
        }

        return answer;
    }

    private String buildUri(VerbDefinition verb) {
        if (path != null && verb.getUri() != null) {
            return path + ":" + verb.getUri();
        } else if (path != null) {
            return path;
        } else if (verb.getUri() != null) {
            return verb.getUri();
        } else {
            return "";
        }
    }

}
