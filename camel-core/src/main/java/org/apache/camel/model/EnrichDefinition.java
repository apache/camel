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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.processor.Enricher;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;enrich/&gt; element
 *
 * @see Enricher
 */
@XmlRootElement(name = "enrich")
@XmlAccessorType(XmlAccessType.FIELD)
public class EnrichDefinition extends NoOutputDefinition<EnrichDefinition> implements EndpointRequiredDefinition {
    @XmlAttribute(name = "uri")
    private String resourceUri;
    // TODO: For Camel 3.0 we should remove this ref attribute as you can do that in the uri, by prefixing with ref:
    @XmlAttribute(name = "ref")
    private String resourceRef;
    @XmlAttribute(name = "strategyRef")
    private String aggregationStrategyRef;
    @XmlAttribute(name = "strategyMethodName")
    private String aggregationStrategyMethodName;
    @XmlAttribute(name = "strategyMethodAllowNull")
    private Boolean aggregationStrategyMethodAllowNull;
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    
    public EnrichDefinition() {
        this(null, null);
    }

    public EnrichDefinition(String resourceUri) {
        this(null, resourceUri);
    }
    
    public EnrichDefinition(AggregationStrategy aggregationStrategy, String resourceUri) {
        this.aggregationStrategy = aggregationStrategy;
        this.resourceUri = resourceUri;
    }
    
    @Override
    public String toString() {
        return "Enrich[" + description() + " " + aggregationStrategy + "]";
    }
    
    protected String description() {
        return FromDefinition.description(resourceUri, resourceRef, (Endpoint) null);
    }
    
    @Override
    public String getLabel() {
        return "enrich[" + description() + "]";
    }

    @Override
    public String getShortName() {
        return "enrich";
    }

    @Override
    public String getEndpointUri() {
        if (resourceUri != null) {
            return resourceUri;
        } else {
            return null;
        }
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        if (ObjectHelper.isEmpty(resourceUri) && ObjectHelper.isEmpty(resourceRef)) {
            throw new IllegalArgumentException("Either uri or ref must be provided for resource endpoint");
        }

        // lookup endpoint
        Endpoint endpoint;
        if (resourceUri != null) {
            endpoint = routeContext.resolveEndpoint(resourceUri);
        } else {
            endpoint = routeContext.resolveEndpoint(null, resourceRef);
        }

        Enricher enricher = new Enricher(null, endpoint.createProducer());
        AggregationStrategy strategy = createAggregationStrategy(routeContext);
        if (strategy == null) {
            enricher.setDefaultAggregationStrategy();
        } else {
            enricher.setAggregationStrategy(strategy);
        }
        return enricher;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = getAggregationStrategy();
        if (strategy == null && aggregationStrategyRef != null) {
            Object aggStrategy = routeContext.lookup(aggregationStrategyRef, Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy) aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, getAggregationStrategyMethodName());
                if (getAggregationStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(getAggregationStrategyMethodAllowNull());
                    adapter.setAllowNullOldExchange(getAggregationStrategyMethodAllowNull());
                }
                strategy = adapter;
            } else {
                throw new IllegalArgumentException("Cannot find AggregationStrategy in Registry with name: " + aggregationStrategyRef);
            }
        }

        if (strategy != null && strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        return strategy;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public String getResourceRef() {
        return resourceRef;
    }

    public void setResourceRef(String resourceRef) {
        this.resourceRef = resourceRef;
    }

    public String getAggregationStrategyRef() {
        return aggregationStrategyRef;
    }

    public void setAggregationStrategyRef(String aggregationStrategyRef) {
        this.aggregationStrategyRef = aggregationStrategyRef;
    }

    public String getAggregationStrategyMethodName() {
        return aggregationStrategyMethodName;
    }

    public void setAggregationStrategyMethodName(String aggregationStrategyMethodName) {
        this.aggregationStrategyMethodName = aggregationStrategyMethodName;
    }

    public Boolean getAggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull;
    }

    public void setAggregationStrategyMethodAllowNull(Boolean aggregationStrategyMethodAllowNull) {
        this.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }
}
