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
package org.apache.camel.component.openshift;

import java.util.List;

import com.openshift.client.IApplication;
import com.openshift.client.IDomain;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;

public class OpenShiftConsumer extends ScheduledPollConsumer {

    public OpenShiftConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public OpenShiftEndpoint getEndpoint() {
        return (OpenShiftEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        String openshiftServer = OpenShiftHelper.getOpenShiftServer(getEndpoint());
        IDomain domain = OpenShiftHelper.loginAndGetDomain(getEndpoint(), openshiftServer);
        if (domain == null) {
            return 0;
        }

        List<IApplication> apps = domain.getApplications();

        // TODO grab state
        // TODO: option to only emit if state changes

        for (IApplication app : apps) {
            Exchange exchange = getEndpoint().createExchange(app);
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error during processing exchange.", exchange, exchange.getException());
            }
        }

        return apps.size();
    }

}
