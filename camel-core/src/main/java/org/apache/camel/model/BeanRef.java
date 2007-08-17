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

import org.apache.camel.Processor;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.impl.RouteContext;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "bean")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeanRef extends OutputType {
    @XmlAttribute(required = true)
    private String ref;
    @XmlAttribute(required = false)
    private String method;
    @XmlTransient
    private Object bean;

    public BeanRef() {
    }

    public BeanRef(String ref) {
        this.ref = ref;
    }

    public BeanRef(String ref, String method) {
        this.ref = ref;
        this.method = method;
    }

    @Override
    public String toString() {
        return "Bean[" + getLabel() + "]";
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setBean(Object bean) {
        this.bean = bean;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        if (bean == null) {
            bean = routeContext.lookup(getRef(), Object.class);
        }
        BeanProcessor answer = new BeanProcessor(bean, routeContext.getCamelContext());
        if (method != null) {
            answer.setMethodName(method);
        }
        return answer;
    }

    @Override
    public String getLabel() {
        if (ref != null) {
           String methodText = "";
            if (method != null) {
                methodText = " method: " + method;
            }
            return "ref: " + ref + methodText;
        }
        else if (bean != null) {
            return bean.toString();
        }
        else {
            return "";
        }
    }
}
