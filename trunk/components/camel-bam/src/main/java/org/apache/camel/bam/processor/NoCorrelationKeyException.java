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
package org.apache.camel.bam.processor;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;

/**
 * An exception thrown if no correlation key could be found for a message
 * exchange preventing any particular orchestration or
 * <a href="http://camel.apache.org/bam.html">BAM</a>
 *
 * @version 
 */
public class NoCorrelationKeyException extends CamelExchangeException {
    private static final long serialVersionUID = 4511220911189364989L;
    private final BamProcessorSupport<?> processor;

    public NoCorrelationKeyException(BamProcessorSupport<?> processor, Exchange exchange) {
        super("No correlation key could be found for " + processor.getCorrelationKeyExpression(), exchange);
        this.processor = processor;
    }

    public BamProcessorSupport<?> getProcessor() {
        return processor;
    }
}
