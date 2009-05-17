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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.processor.loadbalancer.FailOverLoadBalancer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.RouteContext;

@XmlRootElement(name = "failover")
@XmlAccessorType(XmlAccessType.FIELD)
public class FailoverLoadBalancerDefinition extends LoadBalancerDefinition {

    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<String>();

    @Override
    protected LoadBalancer createLoadBalancer(RouteContext routeContext) {
        if (!exceptions.isEmpty()) {
            List<Class> classes = new ArrayList<Class>();
            for (String name : exceptions) {
                Class type = routeContext.getCamelContext().getClassResolver().resolveClass(name);
                if (type == null) {
                    throw new IllegalArgumentException("Cannot find class: " + name + " in the classpath");
                }
                classes.add(type);
            }
            return new FailOverLoadBalancer(classes);
        } else {
            return new FailOverLoadBalancer();
        }
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public String toString() {
        return "FailoverLoadBalancer";
    }
}
