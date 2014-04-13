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
package org.apache.camel.model.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.CircuitBreakerLoadBalancer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;circuitBreaker/&gt; element
 */
@XmlRootElement(name = "circuitBreaker")
@XmlAccessorType(XmlAccessType.FIELD)
public class CircuitBreakerLoadBalancerDefinition extends LoadBalancerDefinition {
    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<String>();
    @XmlAttribute
    private Long halfOpenAfter;
    @XmlAttribute
    private Integer threshold;

    public CircuitBreakerLoadBalancerDefinition() {
    }

    @Override
    protected LoadBalancer createLoadBalancer(RouteContext routeContext) {
        CircuitBreakerLoadBalancer answer;

        if (!exceptions.isEmpty()) {
            List<Class<?>> classes = new ArrayList<Class<?>>();
            for (String name : exceptions) {
                Class<?> type = routeContext.getCamelContext().getClassResolver().resolveClass(name);
                if (type == null) {
                    throw new IllegalArgumentException("Cannot find class: " + name + " in the classpath");
                }
                if (!ObjectHelper.isAssignableFrom(Throwable.class, type)) {
                    throw new IllegalArgumentException("Class is not an instance of Throwable: " + type);
                }
                classes.add(type);
            }
            answer = new CircuitBreakerLoadBalancer(classes);
        } else {
            answer = new CircuitBreakerLoadBalancer();
        }

        if (getHalfOpenAfter() != null) {
            answer.setHalfOpenAfter(getHalfOpenAfter());
        }
        if (getThreshold() != null) {
            answer.setThreshold(getThreshold());
        }
        return answer;
    }

    public Long getHalfOpenAfter() {
        return halfOpenAfter;
    }

    public void setHalfOpenAfter(Long halfOpenAfter) {
        this.halfOpenAfter = halfOpenAfter;
    }

    public Integer getThreshold() {
        return threshold;
    }

    public void setThreshold(Integer threshold) {
        this.threshold = threshold;
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }


    @Override
    public String toString() {
        return "CircuitBreakerLoadBalancer";
    }
}
