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

import org.apache.camel.Processor;
import org.apache.camel.processor.ThrowExceptionProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;throwException/&gt; element
 */
@XmlRootElement(name = "throwException")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThrowExceptionDefinition extends NoOutputDefinition<ThrowExceptionDefinition> {
    @XmlAttribute(required = true)
    // the ref is required from tooling and XML DSL
    private String ref;
    @XmlTransient
    private Exception exception;

    public ThrowExceptionDefinition() {
    }

    @Override
    public String getShortName() {
        return "throwException";
    }

    @Override
    public String toString() {
        return "ThrowException[" + (exception != null ? exception.getClass().getCanonicalName() : "ref:" + ref) + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        if (ref != null && exception == null) {
            this.exception = routeContext.getCamelContext().getRegistry().lookup(ref, Exception.class);
        }

        ObjectHelper.notNull(exception, "exception or ref", this);
        return new ThrowExceptionProcessor(exception);
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }
}