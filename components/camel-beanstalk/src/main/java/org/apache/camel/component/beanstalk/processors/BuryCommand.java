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
package org.apache.camel.component.beanstalk.processors;

import com.surftools.BeanstalkClient.Client;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.beanstalk.BeanstalkEndpoint;
import org.apache.camel.component.beanstalk.BeanstalkExchangeHelper;
import org.apache.camel.component.beanstalk.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuryCommand extends DefaultCommand {
    private static final Logger LOG = LoggerFactory.getLogger(BuryCommand.class);

    public BuryCommand(BeanstalkEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void act(final Client client, final Exchange exchange) throws NoSuchHeaderException {
        final Long jobId = BeanstalkExchangeHelper.getJobID(exchange);
        final long priority = BeanstalkExchangeHelper.getPriority(endpoint, exchange.getIn());
        final boolean result = client.bury(jobId, priority);

        if (!result && LOG.isWarnEnabled()) {
            LOG.warn(String.format("Failed to bury job %d (with priority %d)", jobId, priority));
        } else if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Job %d buried with priority %d. Result is %b", jobId, priority, result));
        }

        answerWith(exchange, Headers.RESULT, result);
    }
}
