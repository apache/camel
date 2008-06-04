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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A useful base class for {@link Predicate} implementations
 * 
 * @version $Revision$
 */
public abstract class BinaryPredicateSupport<E extends Exchange> implements Predicate<E> {

    private final Expression<E> left;
    private final Expression<E> right;

    protected BinaryPredicateSupport(Expression<E> left, Expression<E> right) {
        notNull(left, "left");
        notNull(right, "right");

        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        return left + " " + getOperationText() + " " + right;
    }

    public boolean matches(E exchange) {
        Object leftValue = left.evaluate(exchange);
        Object rightValue = right.evaluate(exchange);
        return matches(exchange, leftValue, rightValue);
    }

    public void assertMatches(String text, E exchange) {
        Object leftValue = left.evaluate(exchange);
        Object rightValue = right.evaluate(exchange);
        if (!matches(exchange, leftValue, rightValue)) {
            throw new AssertionError(text + assertionFailureMessage(exchange, leftValue, rightValue));
        }
    }

    protected abstract boolean matches(E exchange, Object leftValue, Object rightValue);

    protected abstract String getOperationText();

    protected String assertionFailureMessage(E exchange, Object leftValue, Object rightValue) {
        return this + " failed on " + exchange + " with left value <" + leftValue + "> right value <"
               + rightValue + ">";
    }
}
