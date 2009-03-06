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

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;when/&gt; element
 * 
 * @version $Revision$
 */
@XmlRootElement(name = "when")
public class WhenDefinition extends ExpressionNode {

    public WhenDefinition() {
    }

    public WhenDefinition(Predicate predicate) {
        super(predicate);
    }

    public WhenDefinition(ExpressionDefinition expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "When[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "when";
    }

    @Override
    public FilterProcessor createProcessor(RouteContext routeContext) throws Exception {
        return createFilterProcessor(routeContext);
    }
    
    // Fluent API
    //-------------------------------------------------------------------------
    /**
     * Set the expression that WhenType will use
     * @return the builder
     */
    public ExpressionClause<WhenDefinition> expression() {
        return ExpressionClause.createAndSetExpression(this);
    }
}
