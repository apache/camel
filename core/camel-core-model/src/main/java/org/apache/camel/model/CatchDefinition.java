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

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Predicate;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;

/**
 * Catches exceptions as part of a try, catch, finally block
 */
@Metadata(label = "error")
@XmlRootElement(name = "doCatch")
@XmlAccessorType(XmlAccessType.FIELD)
public class CatchDefinition extends OutputDefinition<CatchDefinition> {

    @XmlTransient
    private List<Class<? extends Throwable>> exceptionClasses;

    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<>();
    @XmlElement(name = "onWhen")
    @AsPredicate
    private WhenDefinition onWhen;

    public CatchDefinition() {
    }

    public CatchDefinition(List<Class<? extends Throwable>> exceptionClasses) {
        exception(exceptionClasses);
    }

    public CatchDefinition(Class<? extends Throwable> exceptionType) {
        exception(exceptionType);
    }

    @Override
    public String toString() {
        return "DoCatch[ " + getExceptionClasses() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "doCatch";
    }

    @Override
    public String getLabel() {
        return "doCatch[ " + getExceptionClasses() + "]";
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

    public List<Class<? extends Throwable>> getExceptionClasses() {
        return exceptionClasses;
    }

    public void setExceptionClasses(List<Class<? extends Throwable>> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * The exception(s) to catch.
     *
     * @param  exception one or more exceptions
     * @return           the builder
     */
    public CatchDefinition exception(Class<? extends Throwable> exception) {
        return exception(List.of(exception));
    }

    /**
     * The exception(s) to catch.
     *
     * @param  exception1 fist exception
     * @param  exception2 second exception
     * @return            the builder
     */
    public CatchDefinition exception(Class<? extends Throwable> exception1, Class<? extends Throwable> exception2) {
        return exception(List.of(exception1, exception2));
    }

    /**
     * The exception(s) to catch.
     *
     * @param  exception1 fist exception
     * @param  exception2 second exception
     * @param  exception3 third exception
     * @return            the builder
     */
    public CatchDefinition exception(
            Class<? extends Throwable> exception1, Class<? extends Throwable> exception2,
            Class<? extends Throwable> exception3) {
        return exception(List.of(exception1, exception2, exception3));
    }

    /**
     * The exception(s) to catch.
     *
     * @param  exceptions one or more exceptions
     * @return            the builder
     */
    @SafeVarargs
    public final CatchDefinition exception(Class<? extends Throwable>... exceptions) {
        return exception(List.of(exceptions));
    }

    /**
     * The exception(s) to catch.
     *
     * @param  exceptions one or more exceptions
     * @return            the builder
     */
    public CatchDefinition exception(List<Class<? extends Throwable>> exceptions) {
        if (exceptionClasses == null) {
            exceptionClasses = new ArrayList<>();
        }
        for (Class<? extends Throwable> c : exceptions) {
            this.exceptionClasses.add(c);
            this.exceptions.add(c.getName());
        }
        return this;
    }

    /**
     * Sets an additional predicate that should be true before the onCatch is triggered.
     * <p/>
     * To be used for fine grained controlling whether a thrown exception should be intercepted by this exception type
     * or not.
     *
     * @param  predicate predicate that determines true or false
     * @return           the builder
     */
    public CatchDefinition onWhen(@AsPredicate Predicate predicate) {
        setOnWhen(new WhenDefinition(predicate));
        return this;
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }

    public WhenDefinition getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(WhenDefinition onWhen) {
        this.onWhen = onWhen;
    }

}
