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
import org.apache.camel.Processor;
import org.apache.camel.impl.InterceptEndpoint;
import org.apache.camel.processor.InterceptEndpointProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ProcessorDefinitionHelper;

/**
 * Represents an XML &lt;interceptEndpoint/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "interceptEndpoint")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptEndpointDefinition extends InterceptDefinition {

    // TODO: Support lookup endpoint by ref (requires a bit more work)
    // TODO: Support wildcards for endpoints so you can match by scheme, eg jms:*

    @XmlAttribute(required = true)
    private String uri;

    public InterceptEndpointDefinition() {
    }

    public InterceptEndpointDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        return "InterceptEndpoint[" + uri + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "interceptEndpoint";
    }

    @Override
    public String getLabel() {
        return "interceptEndpoint";
    }


    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // we have to reuse the createProcessor method to build the detour route
        // afterwards we remove this interceptor from the route so the route will
        // not use regular intercptor. The interception by endpoint is triggered
        // by the InterceptEndpoint that handles the intercept routing logic
        Endpoint endpoint = lookupEndpoint(routeContext.getCamelContext());

        // create the detour
        Processor detour = routeContext.createProcessor(this);

        // set the detour on the endpoint proxy
        InterceptEndpoint proxy = routeContext.getCamelContext().getTypeConverter().mandatoryConvertTo(InterceptEndpoint.class, endpoint);
        proxy.setDetour(detour);

        // remove the original intercepted route from the outputs as we do not intercept as the regular interceptor
        // instead we use the proxy endpoints producer do the triggering. That is we trigger when someone sends
        // an exchange to the endpoint, see InterceptEndpoint for details.
        RouteDefinition route = routeContext.getRoute();
        List<ProcessorDefinition> outputs = route.getOutputs();
        outputs.remove(this);

        return new InterceptEndpointProcessor(uri, detour);
    }

    public void proxyEndpoint(CamelContext context) {
        // proxy the endpoint by using the InterceptEndpoint that will proxy
        // the producer so it processes the detour first
        Endpoint endpoint = lookupEndpoint(context);

        // decorate endpoint with our proxy
        boolean skip = stopIntercept != null ? stopIntercept : false;
        InterceptEndpoint proxy = new InterceptEndpoint(endpoint, skip);
        try {
            // add will replace the old one
            context.addEndpoint(proxy.getEndpointUri(), proxy);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    /**
     * This method is <b>only</b> for handling some post configuration
     * that is needed from the Spring DSL side as JAXB does not invoke the fluent
     * builders, so we need to manually handle this afterwards, and since this is
     * an interceptor it has to do a bit of magic logic to fixup to handle predicates
     * with or without proceed/stop set as well.
     */
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        // okay the intercept endpoint works a bit differently than the regular interceptors
        // so we must fix the route definiton yet again

        // we need to fix if the interceptor should only trigger based on a predicate
        // as the regular interceptor will prepare the route "reverse" we gotta move
        // from this output to the otherwise output
        if (usePredicate != null && usePredicate) {
            ChoiceDefinition choice = ProcessorDefinitionHelper.findFirstTypeInOutputs(outputs, ChoiceDefinition.class);
            if (choice != null && outputs.size() > 1) {
                // move this outputs to the otherwise, expect the first one
                // as the first one is the interceptor itself
                for (int i = 1; i < outputs.size(); i++) {
                    ProcessorDefinition out = outputs.get(i);
                    choice.getOtherwise().addOutput(out);
                }
                // remove the moved from the original output, by just keeping the first one
                ProcessorDefinition keep = outputs.get(0);
                clearOutput();
                outputs.add(keep);
            }
        }
    }

    private Endpoint lookupEndpoint(CamelContext context) {
        ObjectHelper.notNull(uri, "uri", this);
        return context.getEndpoint(uri);
    }



}
