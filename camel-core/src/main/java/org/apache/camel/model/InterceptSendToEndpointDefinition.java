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
package org.apache.camel.model;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.InterceptSendToEndpoint;
import org.apache.camel.processor.InterceptEndpointProcessor;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.URISupport;

/**
 * Intercepts messages being sent to an endpoint
 *
 * @version 
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "interceptSendToEndpoint")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptSendToEndpointDefinition extends OutputDefinition<InterceptSendToEndpointDefinition> {

    // TODO: Support lookup endpoint by ref (requires a bit more work)

    // TODO: interceptSendToEndpoint needs to proxy the endpoints at very first
    // so when other processors uses an endpoint its already proxied, see workaround in SendProcessor
    // needed when we haven't proxied beforehand. This requires some work in the route builder in Camel
    // to implement so that should be a part of a bigger rework/improvement in the future

    @XmlAttribute(required = true)
    private String uri;
    @XmlAttribute
    private Boolean skipSendToOriginalEndpoint;

    public InterceptSendToEndpointDefinition() {
    }

    public InterceptSendToEndpointDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        return "InterceptSendToEndpoint[" + uri + " -> " + getOutputs() + "]";
    }

    @Override
    public String getLabel() {
        return "interceptSendToEndpoint[" + uri + "]";
    }

    @Override
    public boolean isAbstract() {
        return true;
    }

    @Override
    public boolean isTopLevelOnly() {
        return true;
    }

    @Override
    public Processor createProcessor(final RouteContext routeContext) throws Exception {
        // create the detour
        final Processor detour = this.createChildProcessor(routeContext, true);
        final String matchURI = getUri();

        // register endpoint callback so we can proxy the endpoint
        routeContext.getCamelContext().addRegisterEndpointCallback(new EndpointStrategy() {
            public Endpoint registerEndpoint(String uri, Endpoint endpoint) {
                if (endpoint instanceof InterceptSendToEndpoint) {
                    // endpoint already decorated
                    return endpoint;
                } else if (matchURI == null || matchPattern(routeContext.getCamelContext(), uri, matchURI)) {
                    // only proxy if the uri is matched decorate endpoint with our proxy
                    // should be false by default
                    boolean skip = getSkipSendToOriginalEndpoint() != null && getSkipSendToOriginalEndpoint();
                    InterceptSendToEndpoint proxy = new InterceptSendToEndpoint(endpoint, skip);
                    proxy.setDetour(detour);
                    return proxy;
                } else {
                    // no proxy so return regular endpoint
                    return endpoint;
                }
            }
        });


        // remove the original intercepted route from the outputs as we do not intercept as the regular interceptor
        // instead we use the proxy endpoints producer do the triggering. That is we trigger when someone sends
        // an exchange to the endpoint, see InterceptSendToEndpoint for details.
        RouteDefinition route = routeContext.getRoute();
        List<ProcessorDefinition<?>> outputs = route.getOutputs();
        outputs.remove(this);

        return new InterceptEndpointProcessor(matchURI, detour);
    }

    /**
     * Does the uri match the pattern.
     *
     * @param camelContext the CamelContext
     * @param uri the uri
     * @param pattern the pattern, which can be an endpoint uri as well
     * @return <tt>true</tt> if matched and we should intercept, <tt>false</tt> if not matched, and not intercept.
     */
    protected boolean matchPattern(CamelContext camelContext, String uri, String pattern) {
        // match using the pattern as-is
        boolean match = EndpointHelper.matchEndpoint(camelContext, uri, pattern);
        if (!match) {
            try {
                // the pattern could be an uri, so we need to normalize it before matching again
                pattern = URISupport.normalizeUri(pattern);
                match = EndpointHelper.matchEndpoint(camelContext, uri, pattern);
            } catch (Exception e) {
                // ignore
            }
        }
        return match;
    }

    /**
     * Applies this interceptor only if the given predicate is true
     *
     * @param predicate  the predicate
     * @return the builder
     */
    public InterceptSendToEndpointDefinition when(@AsPredicate Predicate predicate) {
        WhenDefinition when = new WhenDefinition(predicate);
        addOutput(when);
        return this;
    }

    /**
     * Skip sending the {@link org.apache.camel.Exchange} to the original intended endpoint
     *
     * @return the builder
     */
    public InterceptSendToEndpointDefinition skipSendToOriginalEndpoint() {
        setSkipSendToOriginalEndpoint(Boolean.TRUE);
        return this;
    }

    /**
     * This method is <b>only</b> for handling some post configuration
     * that is needed since this is an interceptor, and we have to do
     * a bit of magic logic to fixup to handle predicates
     * with or without proceed/stop set as well.
     */
    public void afterPropertiesSet() {
        // okay the intercept endpoint works a bit differently than the regular interceptors
        // so we must fix the route definition yet again

        if (getOutputs().size() == 0) {
            // no outputs
            return;
        }

        // if there is a when definition at first, then its a predicate for this interceptor
        ProcessorDefinition<?> first = getOutputs().get(0);
        if (first instanceof WhenDefinition && !(first instanceof WhenSkipSendToEndpointDefinition)) {
            WhenDefinition when = (WhenDefinition) first;

            // create a copy of when to use as replacement
            WhenSkipSendToEndpointDefinition newWhen = new WhenSkipSendToEndpointDefinition();
            newWhen.setExpression(when.getExpression());
            newWhen.setId(when.getId());
            newWhen.setInheritErrorHandler(when.isInheritErrorHandler());
            newWhen.setParent(when.getParent());
            newWhen.setOtherAttributes(when.getOtherAttributes());
            newWhen.setDescription(when.getDescription());

            // move this outputs to the when, expect the first one
            // as the first one is the interceptor itself
            for (int i = 1; i < outputs.size(); i++) {
                ProcessorDefinition<?> out = outputs.get(i);
                newWhen.addOutput(out);
            }
            // remove the moved from the original output, by just keeping the first one
            clearOutput();
            outputs.add(newWhen);
        }
    }

    public Boolean getSkipSendToOriginalEndpoint() {
        return skipSendToOriginalEndpoint;
    }

    /**
     * If set to true then the message is not sent to the original endpoint.
     * By default (false) the message is both intercepted and then sent to the original endpoint.
     */
    public void setSkipSendToOriginalEndpoint(Boolean skipSendToOriginalEndpoint) {
        this.skipSendToOriginalEndpoint = skipSendToOriginalEndpoint;
    }
    
    public String getUri() {
        return uri;
    }

    /**
     * Intercept sending to the uri or uri pattern.
     */
    public void setUri(String uri) {
        this.uri = uri;
    }
}
