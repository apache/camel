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
package org.apache.camel.component.spring.security;

import java.util.List;

import javax.security.auth.Subject;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.RouteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.event.AuthorizationFailureEvent;
import org.springframework.security.access.event.AuthorizedEvent;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;

public class SpringSecurityAuthorizationPolicy extends IdentifiedType implements AuthorizationPolicy, InitializingBean, ApplicationEventPublisherAware {
    private static final Logger LOG = LoggerFactory.getLogger(SpringSecurityAuthorizationPolicy.class);
    private AccessDecisionManager accessDecisionManager;
    private AuthenticationManager authenticationManager;
    private AuthenticationAdapter authenticationAdapter;
    private ApplicationEventPublisher eventPublisher;
    private SpringSecurityAccessPolicy accessPolicy;
    private boolean alwaysReauthenticate;
    private boolean useThreadSecurityContext = true;

    public void beforeWrap(RouteContext routeContext, ProcessorDefinition<?> definition) {
    }

    public Processor wrap(RouteContext routeContext, Processor processor) {
        // wrap the processor with authorizeDelegateProcessor
        return new AuthorizeDelegateProcess(processor);
    }
    
    protected void beforeProcess(Exchange exchange) throws Exception {
        List<ConfigAttribute> attributes = accessPolicy.getConfigAttributes();
        
        try {
            Authentication authToken = getAuthentication(exchange.getIn());
            if (authToken == null) {
                CamelAuthorizationException authorizationException =
                    new CamelAuthorizationException("Cannot find the Authentication instance.", exchange);
                throw authorizationException;
            }
            
            Authentication authenticated = authenticateIfRequired(authToken);
            
            // Attempt authorization with exchange
            try {
                this.accessDecisionManager.decide(authenticated, exchange, attributes);
            } catch (AccessDeniedException accessDeniedException) {
                exchange.getIn().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, getId());
                AuthorizationFailureEvent event = new AuthorizationFailureEvent(exchange, attributes, authenticated,
                        accessDeniedException);
                publishEvent(event);
                throw accessDeniedException;
            }
            publishEvent(new AuthorizedEvent(exchange, attributes, authenticated));
            
        } catch (RuntimeException exception) {
            exchange.getIn().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, getId());
            CamelAuthorizationException authorizationException =
                new CamelAuthorizationException("Cannot access the processor which has been protected.", exchange, exception);
            throw authorizationException;
        }
    }
    
    protected Authentication getAuthentication(Message message) {
        Subject subject = message.getHeader(Exchange.AUTHENTICATION, Subject.class);
        Authentication answer = null;
        if (subject != null) {
            answer = getAuthenticationAdapter().toAuthentication(subject);
        }
        // try to get it from thread context as a fallback
        if (answer == null && useThreadSecurityContext) {
            answer = SecurityContextHolder.getContext().getAuthentication();
            LOG.debug("Get the authentication from SecurityContextHolder");
        }        
        return answer;
    }

    private class AuthorizeDelegateProcess extends DelegateProcessor {
        
        AuthorizeDelegateProcess(Processor processor) {
            super(processor);
        }
        
        public void process(Exchange exchange) throws Exception {
            beforeProcess(exchange);
            processNext(exchange);
        }
        
    }

    public void afterPropertiesSet() throws Exception {
        Assert.notNull(this.authenticationManager, "An AuthenticationManager is required");
        Assert.notNull(this.accessDecisionManager, "An AccessDecisionManager is required");
        Assert.notNull(this.accessPolicy, "The accessPolicy is required");
    }
    
    private Authentication authenticateIfRequired(Authentication authentication) {
        if (authentication.isAuthenticated() && !alwaysReauthenticate) {
            LOG.debug("Previously Authenticated: {}", authentication);
            return authentication;
        }

        authentication = authenticationManager.authenticate(authentication);
        LOG.debug("Successfully Authenticated: {}", authentication);
        return authentication;
    }
    
    private void publishEvent(ApplicationEvent event) {
        if (this.eventPublisher != null) {
            this.eventPublisher.publishEvent(event);
        }
    }

    public AuthenticationAdapter getAuthenticationAdapter() {
        if (authenticationAdapter == null) {
            synchronized (this) {
                if (authenticationAdapter != null) {
                    return authenticationAdapter;
                } else {
                    authenticationAdapter = new DefaultAuthenticationAdapter();
                }
            }
        } 
        return authenticationAdapter;
    }
    
    public void setAuthenticationAdapter(AuthenticationAdapter adapter) {
        this.authenticationAdapter = adapter;
    }
    
    public AccessDecisionManager getAccessDecisionManager() {
        return accessDecisionManager;
    }

    public AuthenticationManager getAuthenticationManager() {
        return this.authenticationManager;
    }

    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }
    
    public void setSpringSecurityAccessPolicy(SpringSecurityAccessPolicy policy) {
        this.accessPolicy = policy;
    }
    
    public SpringSecurityAccessPolicy getSpringSecurityAccessPolicy() {
        return accessPolicy;
    }
    
    public boolean isAlwaysReauthenticate() {
        return alwaysReauthenticate;
    }
    
    public void setAlwaysReauthenticate(boolean alwaysReauthenticate) {
        this.alwaysReauthenticate = alwaysReauthenticate;
    }
    
    public boolean isUseThreadSecurityContext() {
        return useThreadSecurityContext;
    }
    
    public void setUseThreadSecurityContext(boolean useThreadSecurityContext) {
        this.useThreadSecurityContext = useThreadSecurityContext;
    }

    public void setAuthenticationManager(AuthenticationManager newManager) {
        this.authenticationManager = newManager;
    }
    
    public void setAccessDecisionManager(AccessDecisionManager accessDecisionManager) {
        this.accessDecisionManager = accessDecisionManager;
    }
}
