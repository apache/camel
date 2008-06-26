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
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * Base class for interceptor types.
 *
 * @version $Revision$
 */
@XmlRootElement(name = "interceptor")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptorRef extends InterceptorType {
    @XmlAttribute(required = true)
    private String ref;
    @XmlTransient
    private DelegateProcessor interceptor;

    public InterceptorRef() {
    }

    public InterceptorRef(String ref) {
        setRef(ref);
    }

    public InterceptorRef(DelegateProcessor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public String toString() {
        return "Interceptor[" + getLabel() + "]";
    }

    @Override
    public String getShortName() {
        return "interceptor";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        DelegateProcessor processor = createInterceptor(routeContext);
        Processor child = createOutputsProcessor(routeContext);
        processor.setProcessor(child);
        return processor;
    }

    public DelegateProcessor createInterceptor(RouteContext routeContext) {
        if (interceptor == null) {
            interceptor = routeContext.lookup(getRef(), DelegateProcessor.class);
        }
        if (interceptor == null) {
            throw new IllegalArgumentException("No DelegateProcessor bean available for reference: " + getRef());
        }
        return interceptor;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getLabel() {
        if (ref != null) {
            return "ref:  " + ref;
        } else if (interceptor != null) {
            return interceptor.toString();
        } else {
            return "";
        }
    }
    
    /**
     * Get the underlying {@link DelegateProcessor} implementation
     * 
     * @return the {@link DelegateProcessor}
     */
    @XmlTransient
    public DelegateProcessor getInterceptor() {
        return interceptor;
    }
}
