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
package org.apache.camel.component.cedar;

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class CedarConfiguration {
    @UriParam
    @Metadata(required = true,
              description = "Required classpath: or file: Cedar policy text resource URI. Loaded and schema validated at startup and explicit reload.")
    private String policiesResource;
    @UriParam
    @Metadata(required = true,
              description = "Required classpath: or file: Cedar schema text resource URI. Used for policy, entity and request validation.")
    private String schemaResource;
    @UriParam
    @Metadata(description = "Optional classpath: or file: static Cedar entity JSON resource URI. Published coherently with policy and schema.")
    private String entitiesResource;
    @UriParam
    @Metadata(required = true,
              description = "Required configured expression yielding a Cedar EntityUID or canonical entity UID string.")
    private Expression principal;
    @UriParam
    @Metadata(required = true,
              description = "Required configured expression yielding a Cedar EntityUID or canonical entity UID string.")
    private Expression action;
    @UriParam
    @Metadata(required = true,
              description = "Required configured expression yielding a Cedar EntityUID or canonical entity UID string.")
    private Expression resource;
    @UriParam
    @Metadata(description = "Optional explicit expression yielding a context object or map. No implicit body extraction.")
    private Expression context;
    @UriParam
    @Metadata(description = "Optional request scoped entity JSON provider. Provider owns its bounded I/O and freshness contract.")
    private CedarEntityProvider entityProvider;
    @UriParam(defaultValue = "true")
    @Metadata(defaultValue = "true",
              description = "Reject evaluation errors even when the raw engine decision is allow. False explicitly honors Cedar skip-on-error semantics and retains diagnostics.")
    private boolean strict = true;
    @UriParam(defaultValue = "1048576")
    @Metadata(defaultValue = "1048576",
              description = "Maximum bytes per policy, schema, entity resource, explicit context and provider entity JSON. Reload fails without replacing the active revision if exceeded.")
    private int maxResourceSize = 1048576;

    public String getPoliciesResource() {
        return policiesResource;
    }

    /**
     * Required classpath: or file: Cedar policy text resource URI. Loaded and schema validated at startup and explicit
     * reload.
     */
    public void setPoliciesResource(String policiesResource) {
        this.policiesResource = policiesResource;
    }

    public String getSchemaResource() {
        return schemaResource;
    }

    /** Required classpath: or file: Cedar schema text resource URI. Used for policy, entity and request validation. */
    public void setSchemaResource(String schemaResource) {
        this.schemaResource = schemaResource;
    }

    public String getEntitiesResource() {
        return entitiesResource;
    }

    /**
     * Optional classpath: or file: static Cedar entity JSON resource URI. Published coherently with policy and schema.
     */
    public void setEntitiesResource(String entitiesResource) {
        this.entitiesResource = entitiesResource;
    }

    public Expression getPrincipal() {
        return principal;
    }

    /** Required configured expression yielding a Cedar EntityUID or canonical entity UID string. */
    public void setPrincipal(Expression principal) {
        this.principal = principal;
    }

    public Expression getAction() {
        return action;
    }

    /** Required configured expression yielding a Cedar EntityUID or canonical entity UID string. */
    public void setAction(Expression action) {
        this.action = action;
    }

    public Expression getResource() {
        return resource;
    }

    /** Required configured expression yielding a Cedar EntityUID or canonical entity UID string. */
    public void setResource(Expression resource) {
        this.resource = resource;
    }

    public Expression getContext() {
        return context;
    }

    /** Optional explicit expression yielding a context object or map. No implicit body extraction. */
    public void setContext(Expression context) {
        this.context = context;
    }

    public CedarEntityProvider getEntityProvider() {
        return entityProvider;
    }

    /** Optional request scoped entity JSON provider. Provider owns its bounded I/O and freshness contract. */
    public void setEntityProvider(CedarEntityProvider entityProvider) {
        this.entityProvider = entityProvider;
    }

    public boolean isStrict() {
        return strict;
    }

    /**
     * Reject evaluation errors even when the raw engine decision is allow. False explicitly honors Cedar skip-on-error
     * semantics and retains diagnostics.
     */
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public int getMaxResourceSize() {
        return maxResourceSize;
    }

    /**
     * Maximum bytes per policy, schema, entity resource, explicit context and provider entity JSON. Reload fails
     * without replacing the active revision if exceeded.
     */
    public void setMaxResourceSize(int maxResourceSize) {
        this.maxResourceSize = maxResourceSize;
    }
}
