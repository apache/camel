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

import javax.xml.bind.annotation.*;

import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.FileUtil;

import java.util.ArrayList;
import java.util.List;

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
    private List<RestOperationParam> params = new ArrayList<RestOperationParam>();

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

    // used by XML DSL to either select a <to> or <route>
    // so we need to use the common type OptionalIdentifiedDefinition
    @XmlElements({
            @XmlElement(required = false, name = "to", type = ToDefinition.class),
            @XmlElement(required = false, name = "route", type = RouteDefinition.class)}
    )
    private OptionalIdentifiedDefinition<?> toOrRoute;

    // the Java DSL uses the to or route definition directory
    @XmlTransient
    private ToDefinition to;
    @XmlTransient
    private RouteDefinition route;
    @XmlTransient
    private RestDefinition rest;

    @Override
    public String getLabel() {
        if (method != null) {
            return method;
        } else {
            return "verb";
        }
    }

    public List<RestOperationParam> getParams() {
        return params;
    }

    public String getMethod() {
        return method;
    }

    /**
     * The HTTP verb such as GET or POST
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
        String path = this.rest.getPath();

        String s1 = FileUtil.stripTrailingSeparator(path);
        String s2 = FileUtil.stripLeadingSeparator(uri);
        String allPath;
        if (s1 != null && s2 != null) {
            allPath = s1 + "/" + s2;
        } else if (path != null) {
            allPath = path;
        } else {
            allPath = uri;
        }

        // each {} is a parameter
        String[] arr = allPath.split("\\/");
        for (String a: arr) {
            if (a.startsWith("{") && a.endsWith("}")) {
                String key = a.substring(1, a.length() - 1);
                rest.restParam().name(key).type("path").endParam();
            }
        }


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
     * This option will override what may be configured on a parent level
     */
    public void setType(String type) {
        this.type = type;
        String bodyType = type;
        if (type.endsWith("[]")) {
            bodyType = "List[" + bodyType.substring(0, type.length() - 2) + "]";
        }
        rest.restParam().name("body").type("body").dataType(bodyType).endParam();
    }

    public String getOutType() {
        return outType;
    }

    /**
     * Sets the class name to use for binding from POJO to output for the outgoing data
     * This option will override what may be configured on a parent level
     */
    public void setOutType(String outType) {
        this.outType = outType;
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

    public void setTo(ToDefinition to) {
        this.to = to;
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
        // so we defer the verb from the instance type
        if (this instanceof GetVerbDefinition) {
            return "get";
        } else if (this instanceof PostVerbDefinition) {
            return "post";
        } else if (this instanceof PutVerbDefinition) {
            return "put";
        } else if (this instanceof DeleteVerbDefinition) {
            return "delete";
        } else if (this instanceof HeadVerbDefinition) {
            return "head";
        } else {
            return method;
        }
    }

}
