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
import org.apache.camel.processor.RecipientList;

/**
 * Creates a dynamic <a href="http://activemq.apache.org/camel/recipient-list.html">Recipient List</a> pattern.
 *
 * @version $Revision$
 */
public class RecipientListBuilder<E extends Exchange> extends BuilderSupport implements ProcessorFactory {
    private final Expression expression;


    public RecipientListBuilder(FromBuilder parent, Expression expression) {
        super(parent);
        this.expression = expression;
    }

    public Processor createProcessor() {
        return new RecipientList(expression);
    }
}
