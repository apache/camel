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

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;pollEnrich/&gt; element
 *
 * @see org.apache.camel.processor.Enricher
 */
@XmlRootElement(name = "pollEnrich")
@XmlAccessorType(XmlAccessType.FIELD)
public class PollEnrichDefinition extends NoOutputDefinition<PollEnrichDefinition> {

    @XmlAttribute(name = "uri")
    private String resourceUri;
    @XmlAttribute(name = "ref")
    private String resourceRef;
    @XmlAttribute(name = "timeout")
    private Long timeout;
    @XmlAttribute(name = "strategyRef")
    private String aggregationStrategyRef;
    @XmlTransient
    private AggregationStrategy aggregationStrategy;

    public PollEnrichDefinition() {
        this(null, null, 0);
    }

    public PollEnrichDefinition(AggregationStrategy aggregationStrategy, String resourceUri, long timeout) {
        this.aggregationStrategy = aggregationStrategy;
        this.resourceUri = resourceUri;
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return "PollEnrich[" + (resourceUri != null ? resourceUri : "ref:" + resourceRef) + " " + aggregationStrategy + "]";
    }

    @Override
    public String getShortName() {
        return "pollEnrich";
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

        PollEnricher enricher;
        if (timeout != null) {
            enricher = new PollEnricher(null, endpoint.createPollingConsumer(), timeout);
        } else {
            enricher = new PollEnricher(null, endpoint.createPollingConsumer(), 0);
        }

        if (aggregationStrategyRef != null) {
            aggregationStrategy = routeContext.lookup(aggregationStrategyRef, AggregationStrategy.class);
        }
        if (aggregationStrategy == null) {
            enricher.setDefaultAggregationStrategy();
        } else {
            enricher.setAggregationStrategy(aggregationStrategy);
        }

        return enricher;
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

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getAggregationStrategyRef() {
        return aggregationStrategyRef;
    }

    public void setAggregationStrategyRef(String aggregationStrategyRef) {
        this.aggregationStrategyRef = aggregationStrategyRef;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }
}