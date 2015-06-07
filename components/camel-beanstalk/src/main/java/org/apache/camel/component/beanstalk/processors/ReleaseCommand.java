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
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.beanstalk.BeanstalkEndpoint;
import org.apache.camel.component.beanstalk.BeanstalkExchangeHelper;
import org.apache.camel.component.beanstalk.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReleaseCommand extends DefaultCommand {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseCommand.class);

    public ReleaseCommand(BeanstalkEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void act(final Client client, final Exchange exchange) throws NoSuchHeaderException {
        final Message in = exchange.getIn();

        final Long jobId = BeanstalkExchangeHelper.getJobID(exchange);
        final long priority = BeanstalkExchangeHelper.getPriority(endpoint, in);
        final int delay = BeanstalkExchangeHelper.getDelay(endpoint, in);

        final boolean result = client.release(jobId, priority, delay);
        if (!result && LOG.isWarnEnabled()) {
            LOG.warn(String.format("Failed to release job %d (priority %d, delay %d)", jobId, priority, delay));
        } else if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Job %d released with priority %d, delay %d seconds. Result is %b", jobId, priority, delay, result));
        }

        answerWith(exchange, Headers.RESULT, result);
    }
}
