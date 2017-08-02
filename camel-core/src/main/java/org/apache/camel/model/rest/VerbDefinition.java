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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;


import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Rest command
 */
@Metadata(label = "rest")
@XmlRootElement(name = "verb")
@XmlAccessorType(XmlAccessType.FIELD)
public class VerbDefinition extends OptionalIdentifiedDefinition<VerbDefinition> {

    @XmlAttribute
    private String method;

    @XmlElementRef
    private List<RestOperationParamDefinition> params = new ArrayList<RestOperationParamDefinition>();

    @XmlElementRef
    private List<RestOperationResponseMsgDefinition> responseMsgs = new ArrayList<RestOperationResponseMsgDefinition>();

    @XmlAttribute
    private String uri;

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

    @XmlAttribute
    private String type;

    @XmlAttribute
    private String outType;

    // used by XML DSL to either select a <to>, <toD>, or <route>
    // so we need to use the common type OptionalIdentifiedDefinition
    // must select one of them, and hence why they are all set to required = true, but the XSD is set to only allow one of the element
    @XmlElements({
            @XmlElement(required = true, name = "to", type = ToDefinition.class),
            @XmlElement(required = true, name = "toD", type = ToDynamicDefinition.class),
            @XmlElement(required = true, name = "route", type = RouteDefinition.class)}
        )
    private OptionalIdentifiedDefinition<?> toOrRoute;

    // the Java DSL uses the to or route definition directory
    @XmlTransient
    private ToDefinition to;
    @XmlTransient
    private ToDynamicDefinition toD;
    @XmlTransient
    private RouteDefinition route;
    @XmlTransient
    private RestDefinition rest;
    @XmlAttribute
    private String routeId;
    @XmlAttribute
    private Boolean apiDocs;

    @XmlTransient
    private Boolean usedForGeneratingNodeId = Boolean.FALSE;

    @Override
    public String getLabel() {
        if (method != null) {
            return method;
        } else {
            return "verb";
        }
    }

    public List<RestOperationParamDefinition> getParams() {
        return params;
    }

    /**
     * To specify the REST operation parameters using Swagger.
     */
    public void setParams(List<RestOperationParamDefinition> params) {
        this.params = params;
    }

    public List<RestOperationResponseMsgDefinition> getResponseMsgs() {
        return responseMsgs;
    }

    /**
     * Sets swagger operation response messages.
     */
    public void setResponseMsgs(List<RestOperationResponseMsgDefinition> params) {
        this.responseMsgs = responseMsgs;
    }

    public String getMethod() {
        return method;
    }

    /**
     * The HTTP verb such as GET, POST, DELETE, etc.
     */
    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    /**
     * Uri template of this REST service such as /{id}.
     */
    public void setUri(String uri) {
        this.uri = uri;
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

    public String getType() {
        return type;
    }

    /**
     * Sets the class name to use for binding from input to POJO for the incoming data
     * This option will override what may be configured on a parent level.
     * <p/>
     * The canonical name of the class of the input data. Append a [] to the end of the canonical name
     * if you want the input to be an array type.
     */
    public void setType(String type) {
        this.type = type;
    }

    public String getOutType() {
        return outType;
    }

    /**
     * Sets the class name to use for binding from POJO to output for the outgoing data
     * This option will override what may be configured on a parent level
     * <p/>
     * The canonical name of the class of the input data. Append a [] to the end of the canonical name
     * if you want the input to be an array type.
     */
    public void setOutType(String outType) {
        this.outType = outType;
    }

    public String getRouteId() {
        return routeId;
    }

    /**
     * The route id this rest-dsl is using (read-only)
     */
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public Boolean getApiDocs() {
        return apiDocs;
    }

    /**
     * Whether to include or exclude the VerbDefinition in API documentation.
     * <p/>
     * The default value is true.
     */
    public void setApiDocs(Boolean apiDocs) {
        this.apiDocs = apiDocs;
    }

    public RestDefinition getRest() {
        return rest;
    }

    public void setRest(RestDefinition rest) {
        this.rest = rest;
    }

    public RouteDefinition getRoute() {
        if (route != null) {
            return route;
        } else if (toOrRoute instanceof RouteDefinition) {
            return (RouteDefinition) toOrRoute;
        } else {
            return null;
        }
    }

    public void setRoute(RouteDefinition route) {
        this.route = route;
        this.toOrRoute = route;
    }

    public ToDefinition getTo() {
        if (to != null) {
            return to;
        } else if (toOrRoute instanceof ToDefinition) {
            return (ToDefinition) toOrRoute;
        } else {
            return null;
        }
    }

    public ToDynamicDefinition getToD() {
        if (toD != null) {
            return toD;
        } else if (toOrRoute instanceof ToDynamicDefinition) {
            return (ToDynamicDefinition) toOrRoute;
        } else {
            return null;
        }
    }

    public void setTo(ToDefinition to) {
        this.to = to;
        this.toD = null;
        this.toOrRoute = to;
    }

    public void setToD(ToDynamicDefinition to) {
        this.to = null;
        this.toD = to;
        this.toOrRoute = to;
    }

    public OptionalIdentifiedDefinition<?> getToOrRoute() {
        return toOrRoute;
    }

    /**
     * To route from this REST service to a Camel endpoint, or an inlined route
     */
    public void setToOrRoute(OptionalIdentifiedDefinition<?> toOrRoute) {
        this.toOrRoute = toOrRoute;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    public RestDefinition get() {
        return rest.get();
    }

    public RestDefinition get(String uri) {
        return rest.get(uri);
    }

    public RestDefinition post() {
        return rest.post();
    }

    public RestDefinition post(String uri) {
        return rest.post(uri);
    }

    public RestDefinition put() {
        return rest.put();
    }

    public RestDefinition put(String uri) {
        return rest.put(uri);
    }

    public RestDefinition delete() {
        return rest.delete();
    }

    public RestDefinition delete(String uri) {
        return rest.delete(uri);
    }

    public RestDefinition head() {
        return rest.head();
    }

    public RestDefinition head(String uri) {
        return rest.head(uri);
    }

    public RestDefinition verb(String verb) {
        return rest.verb(verb);
    }

    public RestDefinition verb(String verb, String uri) {
        return rest.verb(verb, uri);
    }

    public String asVerb() {
        // we do not want the jaxb model to repeat itself, by outputting <get method="get">
        // so we infer the verb from the instance type
        if (this instanceof GetVerbDefinition) {
            return "get";
        } else if (this instanceof PostVerbDefinition) {
            return "post";
        } else if (this instanceof PutVerbDefinition) {
            return "put";
        } else if (this instanceof PatchVerbDefinition) {
            return "patch";
        } else if (this instanceof DeleteVerbDefinition) {
            return "delete";
        } else if (this instanceof HeadVerbDefinition) {
            return "head";
        } else if (this instanceof OptionsVerbDefinition) {
            return "options";
        } else {
            return method;
        }
    }

    public Boolean getUsedForGeneratingNodeId() {
        return usedForGeneratingNodeId;
    }

    public void setUsedForGeneratingNodeId(Boolean usedForGeneratingNodeId) {
        this.usedForGeneratingNodeId = usedForGeneratingNodeId;
    }
}
