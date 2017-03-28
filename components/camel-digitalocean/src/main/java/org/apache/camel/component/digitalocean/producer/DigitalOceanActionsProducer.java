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
package org.apache.camel.component.digitalocean.producer;

import com.myjeeva.digitalocean.pojo.Action;
import com.myjeeva.digitalocean.pojo.Actions;
import org.apache.camel.Exchange;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.util.ObjectHelper;

/**
 * The DigitalOcean producer for Actions API.
 */
public class DigitalOceanActionsProducer extends DigitalOceanProducer {

    public DigitalOceanActionsProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint, configuration);
    }

    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {

        case list:
            getActions(exchange);
            break;
        case get:
            getAction(exchange);
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation");
        }

    }


    private void getAction(Exchange exchange) throws Exception {
        Integer actionId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);

        if (ObjectHelper.isEmpty(actionId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }
        Action action = getEndpoint().getDigitalOceanClient().getActionInfo(actionId);
        LOG.trace("Action [{}] ", action);
        exchange.getOut().setBody(action);
    }

    private void getActions(Exchange exchange) throws Exception {
        Actions actions = getEndpoint().getDigitalOceanClient().getAvailableActions(configuration.getPage(), configuration.getPerPage());
        LOG.trace("All Actions : page {} / {} per page [{}] ", configuration.getPage(), configuration.getPerPage(), actions.getActions());
        exchange.getOut().setBody(actions.getActions());
    }
}
