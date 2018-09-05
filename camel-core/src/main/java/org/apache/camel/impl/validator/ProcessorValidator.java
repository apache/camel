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
package org.apache.camel.impl.validator;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ValidationException;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Validator} implementation which leverages {@link Processor} to perform validation.
 * 
 * {@see Validator}
 */
public class ProcessorValidator extends Validator {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessorValidator.class);

    private Processor processor;
    private String validatorString;

    public ProcessorValidator(CamelContext context) {
        setCamelContext(context);
    }

    /**
     * Perform content validation with specified type using Processor.
     *
     * @param message message to apply validation
     * @param type 'from' data type
     */
    @Override
    public void validate(Message message, DataType type) throws ValidationException {
        Exchange exchange = message.getExchange();
        
        LOG.debug("Sending to validate processor '{}'", processor);
        DefaultExchange validateExchange = new DefaultExchange(exchange);
        validateExchange.setIn(message);
        validateExchange.setProperties(exchange.getProperties());
        try {
            processor.process(validateExchange);
        } catch (Exception e) {
            if (e instanceof ValidationException) {
                throw (ValidationException)e;
            } else {
                throw new ValidationException(String.format("Validation failed for '%s'", type), validateExchange, e);
            }
        }
    }

    /**
     * Set processor to use
     *
     * @param processor Processor
     * @return this ProcessorTransformer instance
     */
    public ProcessorValidator setProcessor(Processor processor) {
        this.processor = processor;
        this.validatorString = null;
        return this;
    }

    @Override
    public String toString() {
        if (validatorString == null) {
            validatorString =
                String.format("ProcessorValidator[type='%s', processor='%s']", getType(), processor);
        }
        return validatorString;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(processor, "processor", this);
        ServiceHelper.startService(this.processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(this.processor);
    }
}
