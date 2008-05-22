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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * For XQuery expresions and predicates
 *
 * @version $Revision$
 */
@XmlRootElement(name = "xquery")
@XmlAccessorType(XmlAccessType.FIELD)
public class XQueryExpression extends NamespaceAwareExpression {
    private static final transient Log LOG = LogFactory.getLog(XQueryExpression.class);

    @XmlAttribute(required = false)
    private String type;
    @XmlTransient
    private Class resultType;

    public XQueryExpression() {
    }

    public XQueryExpression(String expression) {
        super(expression);
    }

    public String getLanguage() {
        return "xquery";
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Class getResultType() {
        return resultType;
    }

    public void setResultType(Class resultType) {
        this.resultType = resultType;
    }

    @Override
    protected void configureExpression(RouteContext routeContext, Expression expression) {
        super.configureExpression(routeContext, expression);
        updateResultType();
        if (resultType != null) {
            setProperty(expression, "resultType", resultType);
        }
    }

    @Override
    protected void configurePredicate(RouteContext routeContext, Predicate predicate) {
        super.configurePredicate(routeContext, predicate);
        updateResultType();
        if (resultType != null) {
            setProperty(predicate, "resultType", resultType);
        }
    }

    private void updateResultType() {
        if (resultType == null && type != null) {
            try {
                resultType = Class.forName(type);
            } catch (ClassNotFoundException e) {
                LOG.error("ClassNotFoundException creating class: " + type);
            }
        }
    }
}
