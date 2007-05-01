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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Message;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implements a
 * <a href="http://activemq.apache.org/camel/dead-letter-channel.html">Dead Letter Channel</a>
 * after attempting to redeliver the message using the {@link RedeliveryPolicy}
 *
 * @version $Revision$
 */
public class DeadLetterChannel extends ServiceSupport implements ErrorHandler {
    public static final String REDELIVERY_COUNTER = "org.apache.camel.RedeliveryCounter";
    public static final String REDELIVERED = "org.apache.camel.Redelivered";

    private static final transient Log log = LogFactory.getLog(DeadLetterChannel.class);
    private Processor output;
    private Processor deadLetter;
    private RedeliveryPolicy redeliveryPolicy;
    private Logger logger;

    public static <E extends Exchange> Logger createDefaultLogger() {
        return new Logger(log, LoggingLevel.ERROR);
    }

    public DeadLetterChannel(Processor output, Processor deadLetter) {
        this(output, deadLetter, new RedeliveryPolicy(), DeadLetterChannel.createDefaultLogger());
    }

    public DeadLetterChannel(Processor output, Processor deadLetter, RedeliveryPolicy redeliveryPolicy, Logger logger) {
        this.deadLetter = deadLetter;
        this.output = output;
        this.redeliveryPolicy = redeliveryPolicy;
        this.logger = logger;
    }

    @Override
    public String toString() {
        return "DeadLetterChannel[" + output + ", " + deadLetter + ", " + redeliveryPolicy + "]";
    }

    public void process(Exchange exchange) throws Exception {
        int redeliveryCounter = 0;
        long redeliveryDelay = 0;

        do {
            if (redeliveryCounter > 0) {
                // Figure out how long we should wait to resend this message.
                redeliveryDelay = redeliveryPolicy.getRedeliveryDelay(redeliveryDelay);
                sleep(redeliveryDelay);
            }

            try {
                output.process(exchange);
                return;
            }
            catch (RuntimeException e) {
                logger.log("On delivery attempt: " + redeliveryCounter + " caught: " + e, e);
            }
            redeliveryCounter = incrementRedeliveryCounter(exchange);
        }
        while (redeliveryPolicy.shouldRedeliver(redeliveryCounter));

        // now lets send to the dead letter queue
        deadLetter.process(exchange);
    }

    // Properties
    //-------------------------------------------------------------------------

    /**
     * Returns the output processor
     */
    public Processor getOutput() {
        return output;
    }

    /**
     * Returns the dead letter that message exchanges will be sent to if the redelivery attempts fail
     */
    public Processor getDeadLetter() {
        return deadLetter;
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * Sets the redelivery policy
     */
    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Sets the logger strategy; which {@link Log} to use and which {@link LoggingLevel} to use
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * Increments the redelivery counter and adds the redelivered flag if the message has been redelivered
     */
    protected int incrementRedeliveryCounter(Exchange exchange) {
        Message in = exchange.getIn();
        Integer counter = in.getHeader(REDELIVERY_COUNTER, Integer.class);
        int next = 1;
        if (counter != null) {
            next = counter + 1;
        }
        in.setHeader(REDELIVERY_COUNTER, next);
            in.setHeader(REDELIVERED, true);
        return next;
    }

    protected void sleep(long redeliveryDelay) {
        if (redeliveryDelay > 0) {
            if (log.isDebugEnabled()) {
                log.debug("Sleeping for: " + redeliveryDelay + " until attempting redelivery");
            }
            try {
                Thread.sleep(redeliveryDelay);
            }
            catch (InterruptedException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Thread interupted: " + e, e);
                }
            }
        }
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(output, deadLetter);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(deadLetter, output);
    }
}
