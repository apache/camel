/*
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
package org.apache.camel.component.cxf.jaxws;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.rt.security.SecurityConstants;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.ws.policy.PolicyEngine;
import org.apache.cxf.ws.policy.PolicyEngineImpl;
import org.apache.cxf.ws.policy.PolicyProvider;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.neethi.Assertion;
import org.apache.neethi.PolicyComponent;
import org.apache.neethi.PolicyContainingAssertion;
import org.apache.neethi.PolicyOperator;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up a WS-Security UsernameToken from the wsSecurity options of the {@link CxfEndpoint}.
 * <p>
 * It always sets the CXF security properties (user name, password and callback handler), which the WS-SecurityPolicy
 * interceptors use when the WSDL of the service has a WS-SecurityPolicy. When it has none, it also adds a
 * {@link WSS4JOutInterceptor} (producer) or a {@link WSS4JInInterceptor} (consumer) for the UsernameToken. It does not
 * add both, as the security header would then be processed twice.
 * <p>
 * This is the only class of the endpoint options that uses the optional cxf-rt-ws-security dependency, and it is loaded
 * only when one of the options is set.
 */
final class WsSecurityUsernameTokenConfigurer implements CxfConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(WsSecurityUsernameTokenConfigurer.class);

    // the namespaces of WS-SecurityPolicy 1.1, 1.2 and 1.3
    private static final Set<String> SECURITY_POLICY_NAMESPACES = Set.of(
            "http://schemas.xmlsoap.org/ws/2005/07/securitypolicy",
            "http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702",
            "http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200802");

    private final CxfEndpoint endpoint;

    private WsSecurityUsernameTokenConfigurer(CxfEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    static CxfConfigurer create(CxfEndpoint endpoint) {
        return new WsSecurityUsernameTokenConfigurer(endpoint);
    }

    @Override
    public void configure(AbstractWSDLBasedEndpointFactory factoryBean) {
        // the client and server are configured once created, as the policy of the WSDL is known only then
    }

    @Override
    public void configureClient(Client client) {
        String username = endpoint.getWsSecurityUsername();
        String password = endpoint.getWsSecurityPassword();
        CallbackHandler callbackHandler = endpoint.getWsSecurityCallbackHandler();
        if (ObjectHelper.isEmpty(username)) {
            throw new IllegalArgumentException(
                    "The option wsSecurityUsername must be set to send a WS-Security UsernameToken");
        }
        if (ObjectHelper.isEmpty(password) && callbackHandler == null) {
            throw new IllegalArgumentException(
                    "The option wsSecurityPassword or wsSecurityCallbackHandler must be set to send a WS-Security"
                                               + " UsernameToken");
        }
        CallbackHandler handler = callbackHandler != null ? callbackHandler : new PasswordCallbackHandler(username, password);

        Endpoint ep = client.getEndpoint();
        ep.put(SecurityConstants.USERNAME, username);
        if (ObjectHelper.isNotEmpty(password)) {
            ep.put(SecurityConstants.PASSWORD, password);
        }
        ep.put(SecurityConstants.CALLBACK_HANDLER, handler);

        if (hasSecurityPolicy(client.getBus(), ep.getEndpointInfo())) {
            LOG.debug("The WSDL of {} has a WS-SecurityPolicy, which sends the UsernameToken", endpoint);
        } else {
            Map<String, Object> props = new HashMap<>();
            props.put(ConfigurationConstants.ACTION, ConfigurationConstants.USERNAME_TOKEN);
            props.put(ConfigurationConstants.USER, username);
            props.put(ConfigurationConstants.PASSWORD_TYPE, passwordType());
            props.put(ConfigurationConstants.PW_CALLBACK_REF, handler);
            ep.getOutInterceptors().add(new WSS4JOutInterceptor(props));
            LOG.debug("Added a WSS4JOutInterceptor that sends a UsernameToken to {}", endpoint);
        }
    }

    @Override
    public void configureServer(Server server) {
        CallbackHandler handler = serverCallbackHandler();

        Endpoint ep = server.getEndpoint();
        ep.put(SecurityConstants.CALLBACK_HANDLER, handler);

        if (hasSecurityPolicy(endpoint.getBus(), ep.getEndpointInfo())) {
            LOG.debug("The WSDL of {} has a WS-SecurityPolicy, which checks the UsernameToken", endpoint);
        } else {
            Map<String, Object> props = new HashMap<>();
            props.put(ConfigurationConstants.ACTION, ConfigurationConstants.USERNAME_TOKEN);
            // on the inbound side the password type is the type that is required
            props.put(ConfigurationConstants.PASSWORD_TYPE, passwordType());
            props.put(ConfigurationConstants.PW_CALLBACK_REF, handler);
            ep.getInInterceptors().add(new WSS4JInInterceptor(props));
            LOG.debug("Added a WSS4JInInterceptor that requires a UsernameToken to {}", endpoint);
        }
    }

    private CallbackHandler serverCallbackHandler() {
        if (endpoint.getWsSecurityCallbackHandler() != null) {
            return endpoint.getWsSecurityCallbackHandler();
        }
        if (endpoint.getWsSecurityCredentials() != null) {
            return new PasswordCallbackHandler(endpoint.getWsSecurityCredentials());
        }
        String username = endpoint.getWsSecurityUsername();
        String password = endpoint.getWsSecurityPassword();
        if (ObjectHelper.isEmpty(username) || ObjectHelper.isEmpty(password)) {
            throw new IllegalArgumentException(
                    "The options wsSecurityUsername and wsSecurityPassword, or wsSecurityCredentials, or"
                                               + " wsSecurityCallbackHandler must be set to check a WS-Security"
                                               + " UsernameToken");
        }
        return new PasswordCallbackHandler(username, password);
    }

    private String passwordType() {
        return "Digest".equals(endpoint.getWsSecurityPasswordType()) ? WSS4JConstants.PW_DIGEST : WSS4JConstants.PW_TEXT;
    }

    /**
     * Whether the WSDL of the endpoint has a WS-SecurityPolicy, on the service, the port, the binding, or an operation
     * or message of the binding.
     */
    static boolean hasSecurityPolicy(Bus bus, EndpointInfo ei) {
        PolicyEngine engine = bus != null ? bus.getExtension(PolicyEngine.class) : null;
        if (engine == null || !engine.isEnabled() || ei == null) {
            return false;
        }
        if (!(engine instanceof PolicyEngineImpl engineImpl)) {
            LOG.debug("Cannot check the WS-SecurityPolicy with the policy engine {}", engine);
            return false;
        }
        Message message = new MessageImpl();
        for (PolicyProvider provider : engineImpl.getPolicyProviders()) {
            if (isSecurityPolicy(provider.getEffectivePolicy(ei.getService(), message))
                    || isSecurityPolicy(provider.getEffectivePolicy(ei, message))) {
                return true;
            }
            if (ei.getBinding() == null) {
                continue;
            }
            for (BindingOperationInfo boi : ei.getBinding().getOperations()) {
                if (isSecurityPolicy(provider.getEffectivePolicy(boi, message))
                        || boi.getInput() != null && isSecurityPolicy(provider.getEffectivePolicy(boi.getInput(), message))
                        || boi.getOutput() != null
                                && isSecurityPolicy(provider.getEffectivePolicy(boi.getOutput(), message))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSecurityPolicy(PolicyComponent component) {
        if (component instanceof Assertion assertion) {
            if (assertion.getName() != null
                    && SECURITY_POLICY_NAMESPACES.contains(assertion.getName().getNamespaceURI())) {
                return true;
            }
            return assertion instanceof PolicyContainingAssertion pca && isSecurityPolicy(pca.getPolicy());
        }
        if (component instanceof PolicyOperator operator) {
            for (PolicyComponent child : operator.getPolicyComponents()) {
                if (isSecurityPolicy(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sets the password of the user in a {@link WSPasswordCallback}, from a map of user name to password. It sets no
     * password for an unknown user, so that the UsernameToken of that user is rejected.
     */
    static final class PasswordCallbackHandler implements CallbackHandler {

        // a map bean may hold values that are not String, such as a number in YAML
        private final Map<?, ?> passwords;

        PasswordCallbackHandler(String username, String password) {
            this(Map.of(username, password));
        }

        PasswordCallbackHandler(Map<?, ?> passwords) {
            this.passwords = passwords;
        }

        @Override
        public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof WSPasswordCallback pc) {
                    Object password = pc.getIdentifier() != null ? passwords.get(pc.getIdentifier()) : null;
                    if (password != null) {
                        pc.setPassword(password.toString());
                    }
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }
}
