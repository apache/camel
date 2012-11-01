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
package org.apache.camel.component.controlbus;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ServiceStatus;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ExchangeHelper;

/**
 * The control bus producer.
 */
public class ControlBusProducer extends DefaultAsyncProducer {

    public ControlBusProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public ControlBusEndpoint getEndpoint() {
        return (ControlBusEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (getEndpoint().getLanguage() != null) {
            try {
                processByLanguage(exchange, getEndpoint().getLanguage());
            } catch (Exception e) {
                exchange.setException(e);
            }
        } else if (getEndpoint().getAction() != null) {
            try {
                processByAction(exchange, getEndpoint().getRouteId(), getEndpoint().getAction());
            } catch (Exception e) {
                exchange.setException(e);
            }
        }

        callback.done(true);
        return true;
    }

    protected void processByLanguage(Exchange exchange, Language language) throws Exception {
        // create dummy exchange
        Exchange dummy = ExchangeHelper.createCopy(exchange, true);

        String body = dummy.getIn().getMandatoryBody(String.class);
        if (body != null) {
            Expression exp = language.createExpression(body);
            Object out = exp.evaluate(dummy, Object.class);
            if (out != null) {
                exchange.getIn().setBody(out);
            }
        }
    }

    protected void processByAction(Exchange exchange, String id, String action) throws Exception {
        if ("start".equals(action)) {
            getEndpoint().getCamelContext().startRoute(id);
        } else if ("stop".equals(action)) {
            getEndpoint().getCamelContext().stopRoute(id);
        } else if ("status".equals(action)) {
            ServiceStatus status = getEndpoint().getCamelContext().getRouteStatus(id);
            if (status != null) {
                exchange.getIn().setBody(status.name());
            }
        }
    }

}
