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
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;handled/&gt; element
 */
@XmlRootElement(name = "handled")
@XmlAccessorType(XmlAccessType.FIELD)
public class HandledPredicate {
    @XmlElementRef
    private ExpressionType handledPredicate;
    @XmlTransient
    private Predicate predicate;

    public HandledPredicate() {
    }

    public HandledPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    public ExpressionType getHandledPredicate() {
        return handledPredicate;
    }

    public void setHandledPredicate(ExpressionType handledPredicate) {
        this.handledPredicate = handledPredicate;
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    public Predicate createPredicate(RouteContext routeContext) {
        ExpressionType predicateType = getHandledPredicate();
        if (predicateType != null && predicate == null) {
            predicate = predicateType.createPredicate(routeContext);
        }
        return predicate;
    }
}
