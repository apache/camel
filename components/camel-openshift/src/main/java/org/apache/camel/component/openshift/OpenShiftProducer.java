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
import java.util.Map;

import com.openshift.client.ApplicationScale;
import com.openshift.client.IApplication;
import com.openshift.client.IDomain;
import com.openshift.client.IEnvironmentVariable;
import com.openshift.client.IGear;
import com.openshift.client.IGearGroup;
import com.openshift.client.IGearProfile;
import com.openshift.client.OpenShiftException;
import com.openshift.client.cartridge.IDeployedStandaloneCartridge;
import com.openshift.client.cartridge.IEmbeddableCartridge;
import com.openshift.client.cartridge.IEmbeddedCartridge;
import com.openshift.client.cartridge.query.LatestEmbeddableCartridge;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

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
        case getStandaloneCartridge:
            doGetStandaloneCartridge(exchange, domain);
            break;
        case getEmbeddedCartridges:
            doGetEmbeddedCartridges(exchange, domain);
            break;
        case getGitUrl:
            doGetGitUrl(exchange, domain);
            break;
        case addEmbeddedCartridge:
            doAddEmbeddedCartridge(exchange, domain);
            break;
        case removeEmbeddedCartridge:
            doRemoveEmbeddedCartridge(exchange, domain);
            break;
        case scaleUp:
            doScaleUp(exchange, domain);
            break;
        case scaleDown:
            doScaleDown(exchange, domain);
            break;
        case getDeploymentType:
            doGetDeploymentType(exchange, domain);
            break;
        case setDeploymentType:
            doSetDeploymentType(exchange, domain);
            break;
        case addEnvironmentVariable:
            doAddEnvironmentVariable(exchange, domain);
            break;
        case addMultipleEnvironmentVariables:
            doAddMultipleEnvironmentVariables(exchange, domain);
            break;
        case updateEnvironmentVariable:
            doUpdateEnvironmentVariable(exchange, domain);
            break;
        case getAllEnvironmentVariables:
            doGetAllEnvironmentVariables(exchange, domain);
            break;
        case getEnvironmentVariableValue:
            doGetEnvironmentVariableValue(exchange, domain);
            break;
        case removeEnvironmentVariable:
            doRemoveEnvironmentVariable(exchange, domain);
            break;
        case getGearProfile:
            doGetGearProfile(exchange, domain);
            break;
        case addAlias:
            doAddAlias(exchange, domain);
            break;
        case removeAlias:
            doRemoveAlias(exchange, domain);
            break;
        case getAliases:
            doGetAliases(exchange, domain);
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
    
    protected void doGetStandaloneCartridge(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            IDeployedStandaloneCartridge p = app.getCartridge();
            exchange.getIn().setBody(p.getDisplayName());
        }
    }

    protected void doGetEmbeddedCartridges(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            List<IEmbeddedCartridge> p = app.getEmbeddedCartridges();
            exchange.getIn().setBody(p);
        }
    }
    
    protected void doAddEmbeddedCartridge(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String embeddedCartridgeName = exchange.getIn().getHeader(OpenShiftConstants.EMBEDDED_CARTRIDGE_NAME, getEndpoint().getApplication(), String.class);
            if (ObjectHelper.isNotEmpty(embeddedCartridgeName)) {
                IEmbeddedCartridge p = app.addEmbeddableCartridge((new LatestEmbeddableCartridge(embeddedCartridgeName)).get(app));
                exchange.getIn().setBody(p.getDisplayName());
            } else {
                throw new CamelExchangeException("Cartridge not specified", exchange);
            }
        }
    }
    
    protected void doRemoveEmbeddedCartridge(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String embeddedCartridgeName = exchange.getIn().getHeader(OpenShiftConstants.EMBEDDED_CARTRIDGE_NAME, getEndpoint().getApplication(), String.class);
            if (ObjectHelper.isNotEmpty(embeddedCartridgeName)) {
                IEmbeddableCartridge removingCartridge = (new LatestEmbeddableCartridge(embeddedCartridgeName)).get(app);
                for (IEmbeddedCartridge cartridge : app.getEmbeddedCartridges()) {
                    if (cartridge.equals(removingCartridge)) {
                        cartridge.destroy();
                        exchange.getIn().setBody(cartridge.getDisplayName());
                    }
                }
            } else {
                throw new CamelExchangeException("Cartridge not specified", exchange);
            }
        }
    }
    
    protected void doScaleUp(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            try {
                app.scaleUp();
                ApplicationScale result = app.getApplicationScale();
                exchange.getIn().setBody(result.getValue());
            } catch (OpenShiftException e) {
                throw new CamelExchangeException("Application with id " + name + " is not scalable", exchange);
            }
        }
    }
    
    protected void doScaleDown(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            ApplicationScale scale = app.getApplicationScale();
            if (scale.getValue().equals(ApplicationScale.NO_SCALE.getValue())) {
                log.info("Scaling on application with id " + name + " is not enabled");
            } else {
                app.scaleDown();
                ApplicationScale result = app.getApplicationScale();
                exchange.getIn().setBody(result.getValue());
            }
        }
    }
    
    protected void doGetGitUrl(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String gitUrl = app.getGitUrl();
            exchange.getIn().setBody(gitUrl);
        }
    }
    
    protected void doGetDeploymentType(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String deploymentType = app.getDeploymentType();
            exchange.getIn().setBody(deploymentType);
        }
    }
    
    protected void doSetDeploymentType(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String deploymentType = exchange.getIn().getHeader(OpenShiftConstants.DEPLOYMENT_TYPE, getEndpoint().getApplication(), String.class);
            if (ObjectHelper.isNotEmpty(deploymentType)) {
                String result = app.setDeploymentType(deploymentType);
                exchange.getIn().setBody(result);
            } else {
                throw new CamelExchangeException("Deployment Type not specified", exchange);
            }
        }
    }
    
    protected void doAddEnvironmentVariable(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String variableName = exchange.getIn().getHeader(OpenShiftConstants.ENVIRONMENT_VARIABLE_NAME, getEndpoint().getApplication(), String.class);
            String variableValue = exchange.getIn().getHeader(OpenShiftConstants.ENVIRONMENT_VARIABLE_VALUE, getEndpoint().getApplication(), String.class);
            if (!app.canUpdateEnvironmentVariables()) {
                throw new CamelExchangeException("The application with id " + name + " can't update Environment Variables", exchange);
            }
            if (ObjectHelper.isNotEmpty(variableName) && ObjectHelper.isNotEmpty(variableValue)) {
                IEnvironmentVariable result = app.addEnvironmentVariable(variableName, variableValue);
                exchange.getIn().setBody(result.getName());
            } else {
                throw new CamelExchangeException("Environment variable not correctly specified", exchange);
            }
        }
    }
    
    protected void doAddMultipleEnvironmentVariables(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            Map environmentVariables = exchange.getIn().getHeader(OpenShiftConstants.ENVIRONMENT_VARIABLE_MAP, getEndpoint().getApplication(), Map.class);
            if (!app.canUpdateEnvironmentVariables()) {
                throw new CamelExchangeException("The application with id " + name + " can't update Environment Variables", exchange);
            }
            if (ObjectHelper.isNotEmpty(environmentVariables)) {
                Map<String, IEnvironmentVariable> result = app.addEnvironmentVariables(environmentVariables);
                exchange.getIn().setBody(result);
            } else {
                throw new CamelExchangeException("Environment variables not correctly specified", exchange);
            }
        }
    }
    
    protected void doUpdateEnvironmentVariable(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String variableName = exchange.getIn().getHeader(OpenShiftConstants.ENVIRONMENT_VARIABLE_NAME, getEndpoint().getApplication(), String.class);
            String variableValue = exchange.getIn().getHeader(OpenShiftConstants.ENVIRONMENT_VARIABLE_VALUE, getEndpoint().getApplication(), String.class);
            if (!app.canUpdateEnvironmentVariables()) {
                throw new CamelExchangeException("The application with id " + name + " can't update Environment Variables", exchange);
            }
            if (ObjectHelper.isNotEmpty(variableName) && ObjectHelper.isNotEmpty(variableValue)) {
                IEnvironmentVariable result = app.updateEnvironmentVariable(variableName, variableValue);
                exchange.getIn().setBody(result.getName());
            } else {
                throw new CamelExchangeException("Environment variable not correctly specified", exchange);
            }
        }
    }
    
    protected void doGetEnvironmentVariableValue(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String variableName = exchange.getIn().getHeader(OpenShiftConstants.ENVIRONMENT_VARIABLE_NAME, getEndpoint().getApplication(), String.class);
            if (!app.canGetEnvironmentVariables()) {
                throw new CamelExchangeException("The application with id " + name + " can't get Environment Variables", exchange);
            }
            if (ObjectHelper.isNotEmpty(variableName)) {
                IEnvironmentVariable result = app.getEnvironmentVariable(variableName);
                exchange.getIn().setBody(result.getValue());
            } else {
                throw new CamelExchangeException("Environment variable name not specified", exchange);
            }
        }
    }
    
    protected void doGetAllEnvironmentVariables(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            Map<String, IEnvironmentVariable> result = app.getEnvironmentVariables();
            exchange.getIn().setBody(result);
        }
    }
    
    protected void doRemoveEnvironmentVariable(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String variableName = exchange.getIn().getHeader(OpenShiftConstants.ENVIRONMENT_VARIABLE_NAME, getEndpoint().getApplication(), String.class);
            if (!app.canGetEnvironmentVariables()) {
                throw new CamelExchangeException("The application with id " + name + " can't get Environment Variables", exchange);
            }
            if (ObjectHelper.isNotEmpty(variableName)) {
                app.removeEnvironmentVariable(variableName);
                exchange.getIn().setBody(variableName);
            } else {
                throw new CamelExchangeException("Environment variable name not specified", exchange);
            }
        }
    }
    
    protected void doGetGearProfile(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            IGearProfile result = app.getGearProfile();
            exchange.getIn().setBody(result.getName());
        }
    }
    
    protected void doAddAlias(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String alias = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION_ALIAS, getEndpoint().getApplication(), String.class);
            if (!app.canGetEnvironmentVariables()) {
                throw new CamelExchangeException("The application with id " + name + " can't get Environment Variables", exchange);
            }
            if (ObjectHelper.isNotEmpty(alias)) {
                app.addAlias(alias);
                exchange.getIn().setBody(alias);
            } else {
                throw new CamelExchangeException("Application Alias name not specified", exchange);
            }
        }
    }
    
    protected void doRemoveAlias(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            String alias = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION_ALIAS, getEndpoint().getApplication(), String.class);
            if (!app.canGetEnvironmentVariables()) {
                throw new CamelExchangeException("The application with id " + name + " can't get Environment Variables", exchange);
            }
            if (ObjectHelper.isNotEmpty(alias)) {
                app.removeAlias(alias);
                exchange.getIn().setBody(alias);
            } else {
                throw new CamelExchangeException("Application Alias not specified", exchange);
            }
        }
    }
    
    protected void doGetAliases(Exchange exchange, IDomain domain) throws CamelExchangeException {
        String name = exchange.getIn().getHeader(OpenShiftConstants.APPLICATION, getEndpoint().getApplication(), String.class);
        if (name == null) {
            throw new CamelExchangeException("Application not specified", exchange);
        }

        IApplication app = domain.getApplicationByName(name);
        if (app == null) {
            throw new CamelExchangeException("Application with id " + name + " not found.", exchange);
        } else {
            List<String> aliases = app.getAliases();
            exchange.getIn().setBody(aliases);
        }
    }
}
