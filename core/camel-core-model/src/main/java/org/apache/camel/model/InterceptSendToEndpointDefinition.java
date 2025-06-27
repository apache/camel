/*
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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Predicate;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;

/**
 * Intercepts messages being sent to an endpoint
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "interceptSendToEndpoint")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptSendToEndpointDefinition extends OutputDefinition<InterceptSendToEndpointDefinition> {

    @XmlAttribute(required = true)
    private String uri;
    @XmlAttribute
    private String skipSendToOriginalEndpoint;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String afterUri;
    @Metadata(description = "To use an expression to only trigger intercepting sending to an endpoint in specific situations")
    @XmlElement
    @AsPredicate
    private OnWhenDefinition onWhen;

    public InterceptSendToEndpointDefinition() {
    }

    protected InterceptSendToEndpointDefinition(InterceptSendToEndpointDefinition source) {
        super(source);
        this.uri = source.uri;
        this.skipSendToOriginalEndpoint = source.skipSendToOriginalEndpoint;
        this.afterUri = source.afterUri;
        this.onWhen = source.onWhen != null ? source.onWhen.copyDefinition() : null;
    }

    public InterceptSendToEndpointDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public InterceptSendToEndpointDefinition copyDefinition() {
        return new InterceptSendToEndpointDefinition(this);
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    @Override
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        super.setOutputs(outputs);
    }

    public OnWhenDefinition getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(OnWhenDefinition onWhen) {
        this.onWhen = onWhen;
    }

    @Override
    public String toString() {
        return "InterceptSendToEndpoint[" + uri + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "interceptSendToEndpoint";
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

    /**
     * Applies this interceptor only if the given predicate is true
     *
     * @param      predicate the predicate
     * @return               the builder
     * @deprecated           use {@link #onWhen(Predicate)}
     */
    @Deprecated
    public InterceptSendToEndpointDefinition when(@AsPredicate Predicate predicate) {
        return onWhen(predicate);
    }

    /**
     * Applies this interceptor only if the given predicate is true
     *
     * @param  predicate the predicate
     * @return           the builder
     */
    public InterceptSendToEndpointDefinition onWhen(@AsPredicate Predicate predicate) {
        setOnWhen(new OnWhenDefinition(predicate));
        return this;
    }

    /**
     * Skip sending the {@link org.apache.camel.Exchange} to the original intended endpoint
     *
     * @return the builder
     */
    public InterceptSendToEndpointDefinition skipSendToOriginalEndpoint() {
        setSkipSendToOriginalEndpoint(Boolean.toString(true));
        return this;
    }

    /**
     * After sending to the endpoint then send the message to this url which allows to process its result.
     *
     * @return the builder
     */
    public InterceptSendToEndpointDefinition afterUri(String uri) {
        setAfterUri(uri);
        return this;
    }

    /**
     * This method is <b>only</b> for handling some post configuration that is needed since this is an interceptor, and
     * we have to do a bit of magic logic to fixup to handle predicates with or without proceed/stop set as well.
     */
    @Deprecated
    public void afterPropertiesSet() {
        /**
         * if (getOutputs().isEmpty()) { // no outputs return; } if (onWhen != null) { // change onWhen to when that
         * also includes the outputs // so they are only triggered if the predicate matches at runtime WhenDefinition
         * copy = new WhenDefinition(onWhen); copy.setParent(this); for (ProcessorDefinition<?> out : outputs) {
         * copy.addOutput(out); } clearOutput(); outputs.add(copy); }
         **/
    }

    public String getSkipSendToOriginalEndpoint() {
        return skipSendToOriginalEndpoint;
    }

    /**
     * If set to true then the message is not sent to the original endpoint. By default (false) the message is both
     * intercepted and then sent to the original endpoint.
     */
    public void setSkipSendToOriginalEndpoint(String skipSendToOriginalEndpoint) {
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

    public String getAfterUri() {
        return afterUri;
    }

    /**
     * After sending to the endpoint then send the message to this uri which allows to process its result.
     */
    public void setAfterUri(String afterProcessor) {
        this.afterUri = afterProcessor;
    }

}
