/*
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
package org.apache.camel;

/**
 * A predicate which evaluates a binary expression.
 * <p/>
 * The predicate has a left and right hand side expressions which
 * is matched based on an operator.
 * <p/>
 * This predicate offers the {@link #matchesReturningFailureMessage} method
 * which evaluates and returns a detailed failure message if the predicate did not match.
 */
public interface BinaryPredicate extends Predicate {

    /**
     * Gets the operator
     *
     * @return the operator text
     */
    String getOperator();

    /**
     * Gets the left hand side expression
     *
     * @return the left expression
     */
    Expression getLeft();

    /**
     * Gets the right hand side expression
     *
     * @return the right expression
     */
    Expression getRight();

    /**
     * Evaluates the predicate on the message exchange and returns <tt>null</tt> if this
     * exchange matches the predicate. If it did <b>not</b> match, then a failure message
     * is returned detailing the reason, which can be used by end users to understand
     * the failure.
     *
     * @param exchange the message exchange
     * @return <tt>null</tt> if the predicate matches.
     */
    String matchesReturningFailureMessage(Exchange exchange);

}
