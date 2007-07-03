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
import org.apache.camel.processor.DelayerProcessor;

/**
 * 
 * @version $Revision: 1.1 $
 */
public class DelayerBuilder extends FromBuilder {
    private final Expression<Exchange> processAtExpression;
    private long delay;
    private long batchTimeout = 0L;
    private int batchSize = 1;

    public DelayerBuilder(FromBuilder builder, Expression<Exchange> processAtExpression, long delay) {
        super(builder);
        this.delay = delay;
        this.processAtExpression = processAtExpression;
    }

    @Override
    public Processor createProcessor() throws Exception {
        final Processor processor = super.createProcessor();
        return new DelayerProcessor(processor, processAtExpression, delay);
    }
}
