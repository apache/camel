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
package org.apache.camel.builder;

import org.apache.camel.BinaryPredicate;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A useful base class for {@link Predicate} implementations
 *
 * @version $Revision$
 */
public abstract class BinaryPredicateSupport implements BinaryPredicate {

    private final Expression left;
    private final Expression right;
    private Object lastLeftValue;
    private Object lastRightValue;

    protected BinaryPredicateSupport(Expression left, Expression right) {
        notNull(left, "left");
        notNull(right, "right");

        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        return left + " " + getOperationText() + " " + right;
    }

    public boolean matches(Exchange exchange) {
        // must be thread safe and store result in local objects
        Object leftValue = left.evaluate(exchange, Object.class);
        Object rightValue = right.evaluate(exchange, Object.class);
        // remember last result (may not be thread safe)
        lastRightValue = rightValue;
        lastLeftValue = leftValue;
        return matches(exchange, leftValue, rightValue);
    }

    protected abstract boolean matches(Exchange exchange, Object leftValue, Object rightValue);

    protected abstract String getOperationText();

    public Expression getRight() {
        return right;
    }

    public Expression getLeft() {
        return left;
    }

    public String getOperator() {
        return getOperationText();
    }

    public Object getRightValue() {
        return lastRightValue;
    }

    public Object getLeftValue() {
        return lastLeftValue;
    }
}
