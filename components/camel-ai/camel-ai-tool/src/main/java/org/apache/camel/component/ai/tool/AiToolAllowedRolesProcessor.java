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
package org.apache.camel.component.ai.tool;

import java.util.Collection;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * Enforces the {@code allowedRoles} option of an {@code ai-tool} endpoint before the wrapped processor runs: the call
 * passes when the caller has one of the named roles, or all of them with {@code allRolesRequired}.
 * <p>
 * The caller's roles are read only from the {@link AiToolConstants#SECURITY_PRINCIPAL_ROLES_PROPERTY} exchange
 * property. On a tool route the message headers carry the tool arguments, which the model controls, so a role named in
 * a header or in an argument never counts. A call without the property, or with a property that is not a collection, is
 * refused.
 * <p>
 * An {@code allowedRoles} that is set but names no role refuses every call: a check with nothing to check fails closed,
 * it does not disappear. This holds with {@code allRolesRequired} too, where "all of no roles" would otherwise pass.
 * <p>
 * A refusal is a {@link CamelAuthorizationException} set on the exchange, as the shipped security policies do under the
 * asynchronous routing engine. {@link AiToolExecutor} turns it into the generic refusal the model gets, logs its
 * message and fires {@link AiToolAuthorizationDeniedEvent}. The message names the tool, the roles it asks for and the
 * caller, and goes to the log only.
 * <p>
 * It extends {@link DelegateAsyncProcessor} (as the {@link org.apache.camel.spi.Policy} SPI advises), so the processor
 * it guards is started and stopped with it and keeps running on the asynchronous routing engine.
 */
final class AiToolAllowedRolesProcessor extends DelegateAsyncProcessor {

    /**
     * What {@link CamelAuthorizationException#getPolicyId()} returns for a call this check refused: the name of the
     * option.
     */
    static final String POLICY_ID = "allowedRoles";

    private final String toolName;
    private final List<String> allowedRoles;
    private final boolean allRolesRequired;

    AiToolAllowedRolesProcessor(Processor processor, String toolName, List<String> allowedRoles, boolean allRolesRequired) {
        super(processor);
        this.toolName = toolName;
        this.allowedRoles = allowedRoles;
        this.allRolesRequired = allRolesRequired;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Object callerRoles = exchange.getProperty(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY);
        if (allowedRoles.isEmpty() || !(callerRoles instanceof Collection<?> roles) || !isAllowed(roles)) {
            exchange.getMessage().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, POLICY_ID);
            exchange.setException(new CamelAuthorizationException(refusal(exchange, callerRoles), exchange));
            callback.done(true);
            return true;
        }
        return super.process(exchange, callback);
    }

    private boolean isAllowed(Collection<?> callerRoles) {
        return allRolesRequired
                ? allowedRoles.stream().allMatch(role -> hasRole(callerRoles, role))
                : allowedRoles.stream().anyMatch(role -> hasRole(callerRoles, role));
    }

    /**
     * Compares with {@link String#equals(Object)} rather than {@link Collection#contains(Object)}, so the comparison is
     * exact whatever collection carries the caller's roles.
     */
    private static boolean hasRole(Collection<?> callerRoles, String role) {
        return callerRoles.stream().anyMatch(role::equals);
    }

    private String refusal(Exchange exchange, Object callerRoles) {
        if (allowedRoles.isEmpty()) {
            return "Tool '" + toolName + "' sets an allowedRoles that names no role, so every call is refused";
        }
        String name = exchange.getProperty(AiToolConstants.SECURITY_PRINCIPAL_NAME_PROPERTY, String.class);
        String caller = name != null ? "caller '" + name + "'" : "the caller";
        String needs = "Tool '" + toolName + "' needs " + (allRolesRequired ? "all" : "one") + " of the roles "
                       + allowedRoles;
        if (callerRoles == null) {
            // the MCP bridge leaves the property out for a caller who has no roles
            return needs + "; " + caller + " has no roles";
        }
        if (!(callerRoles instanceof Collection)) {
            // a property of another type: what to fix is who sets it, not the caller's roles
            return needs + "; " + caller + " has no collection of roles in the exchange property "
                   + AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY;
        }
        return needs + "; " + caller + (allRolesRequired ? " does not have all of them" : " has none of them");
    }
}
