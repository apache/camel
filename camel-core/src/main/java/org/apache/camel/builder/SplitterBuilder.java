/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.camel.Processor;
import org.apache.camel.processor.Splitter;

/**
 * A builder for the <a href="http://activemq.apache.org/camel/splitter.html">Splitter</a> pattern
 * where an expression is evaluated to iterate through each of the parts of a message and then each part is then send to some endpoint.

 * @version $Revision$
 */
public class SplitterBuilder extends FromBuilder {
    private final Expression expression;

    public SplitterBuilder(FromBuilder parent, Expression expression) {
        super(parent);
        this.expression = expression;
    }

    public Processor createProcessor() throws Exception {
        // lets create a single processor for all child predicates
        Processor destination = super.createProcessor();
        return new Splitter(destination, expression);
    }
}
