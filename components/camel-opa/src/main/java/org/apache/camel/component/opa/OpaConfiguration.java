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
package org.apache.camel.component.opa;

import com.styra.opa.OPAClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class OpaConfiguration implements Cloneable {

    @UriParam(defaultValue = "http://localhost:8181")
    private String serverUrl = "http://localhost:8181";

    @UriParam(defaultValue = "allow")
    private String allowKey = "allow";

    @UriParam(defaultValue = "*")
    private String includeHeaders = "*";

    @UriParam
    private String includeProperties;

    @UriParam
    private boolean includeBody;

    @UriParam(label = "security", secret = true)
    private String bearerToken;

    @UriParam(label = "security", security = "insecure:dev")
    private boolean failOpen;

    @UriParam(label = "advanced",
              description = "An existing OPAClient to use. When set, serverUrl and bearerToken are ignored.")
    @Metadata(autowired = true)
    private OPAClient opaClient;

    /**
     * The base URL of the OPA server, without the {@code /v1/data} suffix. The default assumes OPA running as a sidecar
     * on the standard port.
     */
    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * The key to read the allow/deny verdict from when the policy returns an object rather than a plain boolean. For a
     * policy returning <code>{"allow": true, "reasons": []}</code> the default value of {@code allow} is what you want.
     */
    public String getAllowKey() {
        return allowKey;
    }

    public void setAllowKey(String allowKey) {
        this.allowKey = allowKey;
    }

    /**
     * Comma-separated list of message header names to send to OPA in the input document. The default of {@code *} sends
     * every header. Narrow it when the policy only needs a few headers, or when the message carries headers that should
     * not leave the JVM.
     */
    public String getIncludeHeaders() {
        return includeHeaders;
    }

    public void setIncludeHeaders(String includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    /**
     * Comma-separated list of exchange property names to send to OPA in the input document, or {@code *} for all of
     * them. Empty by default, so no properties are sent unless asked for.
     * <p/>
     * This is where the authentication components put the identity they verified: {@code camel-keycloak} stores the
     * access token and its subject as exchange properties and prefers them over the equivalent headers, precisely
     * because headers can be set by the caller. List those property names here to let a policy authorize the identity
     * an earlier step established, instead of copying it into a header first. Only custom properties are sent; Camel's
     * own internal exchange properties are never included.
     */
    public String getIncludeProperties() {
        return includeProperties;
    }

    public void setIncludeProperties(String includeProperties) {
        this.includeProperties = includeProperties;
    }

    /**
     * Whether to send the message body to OPA as part of the input document. Disabled by default: bodies can be large
     * or streaming, and most authorization decisions only need headers. When enabled on a streaming body, enable stream
     * caching so that the body is still readable by the rest of the route.
     */
    public boolean isIncludeBody() {
        return includeBody;
    }

    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    /**
     * Bearer token sent to the OPA server in the Authorization header, for an OPA instance that has its API
     * authentication enabled.
     */
    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    /**
     * Whether to allow the exchange to proceed when the policy cannot be evaluated at all, for example because the OPA
     * server is unreachable. Disabled by default so that an unreachable policy decision point denies rather than grants
     * access. Do not enable this in production.
     */
    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    /**
     * An already-configured {@link OPAClient} to use instead of letting the endpoint create one.
     */
    public OPAClient getOpaClient() {
        return opaClient;
    }

    public void setOpaClient(OPAClient opaClient) {
        this.opaClient = opaClient;
    }

    public OpaConfiguration copy() {
        try {
            return (OpaConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
