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

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.openshift.client.IApplication;
import com.openshift.client.IDomain;
import com.openshift.client.IGear;
import com.openshift.client.IGearGroup;
import com.openshift.client.cartridge.IEmbeddedCartridge;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

public class OpenShiftProducer extends DefaultProducer {

    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public OpenShiftProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenShiftEndpoint getEndpoint() {
        return (OpenShiftEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String openshiftServer = OpenShiftHelper.getOpenShiftServer(getEndpoint());
        IDomain domain = OpenShiftHelper.loginAndGetDomain(getEndpoint(), openshiftServer);
        if (domain == null) {
            throw new CamelExchangeException("User has no domain with id " + getEndpoint().getDomain(), exchange);
        }

        OpenShiftOperation operation = exchange.getIn().getHeader(OpenShiftConstants.OPERATION, getEndpoint().getOperation(), OpenShiftOperation.class);

        switch (operation) {
        case start:
            doStart(exchange, domain);
            break;
        case stop:
            doStop(exchange, domain);
            break;
        case restart:
            doRestart(exchange, domain);
            break;
        case state:
            doState(exchange, domain);
            break;
        case list:
        default:
            // and do list by default
            if (getEndpoint().getMode().equals("json")) {
                doListJson(exchange, domain);
            } else {
                doListPojo(exchange, domain);
            }
            break;
        }
    }

    protected void doListJson(Exchange exchange, IDomain domain) {
        StringBuilder sb = new StringBuilder("{\n  \"applications\": [");

        boolean first = true;
        for (IApplication application : domain.getApplications()) {
            if (!first) {
                sb.append("\n    ],");
            } else {
                first = false;
            }

            String date = new SimpleDateFormat(TIMESTAMP_FORMAT).format(application.getCreationTime());

            // application
            sb.append("\n    {");
            sb.append("\n      \"uuid\": \"" + application.getUUID() + "\",");
            sb.append("\n      \"domain\": \"" + application.getDomain().getId() + "\",");
            sb.append("\n      \"name\": \"" + application.getName() + "\",");
            sb.append("\n      \"creationTime\": \"" + date + "\",");
            sb.append("\n      \"applicationUrl\": \"" + application.getApplicationUrl() + "\",");
            sb.append("\n      \"gitUrl\": \"" + application.getGitUrl() + "\",");
            sb.append("\n      \"sshUrl\": \"" + application.getSshUrl() + "\",");

            // catridge
            sb.append("\n      \"catridge\": {");
            sb.append("\n        \"name\": \"" + application.getCartridge().getName() + "\",");
            sb.append("\n        \"displayName\": \"" + application.getCartridge().getDisplayName() + "\",");
            sb.append("\n        \"description\": \"" + application.getCartridge().getDescription() + "\"");
            sb.append("\n      },");

            // embedded catridges
            List<IEmbeddedCartridge> embeddedCartridges = application.getEmbeddedCartridges();
            if (embeddedCartridges != null && !embeddedCartridges.isEmpty()) {
                sb.append("\n      \"embeddedCatridges\": [");
                for (Iterator<IEmbeddedCartridge> it = embeddedCartridges.iterator(); it.hasNext();) {
                    IEmbeddedCartridge cartridge = it.next();
                    sb.append("\n      \"catridge\": {");
                    sb.append("\n        \"name\": \"" + cartridge.getName() + "\",");
                    sb.append("\n        \"displayName\": \"" + cartridge.getDisplayName() + "\",");
                    sb.append("\n        \"description\": \"" + cartridge.getDescription() + "\"");
                    sb.append("\n      }");
                    if (it.hasNext()) {
                        sb.append(",");
                    }
                }
                sb.append("\n      ]");
            }

            sb.append("\n      \"gearProfile\": \"" + application.getGearProfile().getName() + "\",");
            sb.append("\n      \"gears\": [");
            boolean firstGear = true;
            for (IGearGroup group : application.getGearGroups()) {
                for (IGear gear : group.getGears()) {
                    if (!firstGear) {
                        sb.append(",");
                    } else {
                        firstGear = false;
                    }
                    sb.append("\n        {");
                    sb.append("\n         \"id\": \"" + gear.getId() + "\",");
                    sb.append("\n         \"sshUrl\": \"" + gear.getSshUrl() + "\",");
                    sb.append("\n         \"state\": \"" + gear.getState().getState().toLowerCase(Locale.ENGLISH) + "\"");
                    sb.append("\n        }");
                }
            }
            sb.append("\n      ]");
            sb.append("\n    }");
        }
        sb.append("\n  ]");
        sb.append("\n}");

        exchange.getIn().setBody(sb.toString());
    }

    protected void doListPojo(Exchange exchange, IDomain domain) {
        exchange.getIn().setBody(domain.getApplications());
    }

    protected void doStart(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            app.start();
        }
    }

    protected void doStop(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            app.stop();
        }
    }

    protected void doRestart(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            app.restart();
        }
    }

    protected void doState(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String state = OpenShiftHelper.getStateForApplication(app);
            exchange.getIn().setBody(state);
        }
    }

}
