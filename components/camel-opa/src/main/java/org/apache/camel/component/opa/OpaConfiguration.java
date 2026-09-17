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
import org.apache.camel.support.jsse.SSLContextParameters;

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

    @UriParam(label = "security", security = "secret")
    private String bearerToken;

    @UriParam(defaultValue = "rest", enums = "rest,wasm")
    private String evaluationMode = "rest";

    @UriParam
    private String policyBundle;

    @UriParam
    private String entrypoint;

    @UriParam(label = "advanced", defaultValue = "8")
    private int poolSize = 8;
    @UriParam(label = "advanced", defaultValue = "30000", javaType = "java.time.Duration")
    private long borrowTimeout = 30000;

    @UriParam(label = "advanced", defaultValue = "10000", javaType = "java.time.Duration")
    private long connectionTimeout = 10000;
    @UriParam(label = "advanced", defaultValue = "30000", javaType = "java.time.Duration")
    private long requestTimeout = 30000;

    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

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
     * <p/>
     * A dotted path reaches a verdict nested inside the document: {@code allowKey=result.allow} reads
     * <code>{"result": {"allow": true}}</code>. A key with no dot is looked up directly at the top level.
     */
    public String getAllowKey() {
        return allowKey;
    }

    public void setAllowKey(String allowKey) {
        this.allowKey = allowKey;
    }

    /**
     * Comma-separated list of message header names to send to OPA in the input document. The default of {@code *} sends
     * every header <em>except</em> those that carry a caller credential verbatim - {@code Authorization},
     * {@code Proxy-Authorization}, {@code Cookie} and {@code Set-Cookie} - which are withheld because OPA's decision
     * logging ships the whole input document, often off the box. A policy that genuinely needs one can still have it by
     * naming the header here. Narrow the list when the policy only needs a few headers.
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

    public String getEvaluationMode() {
        return evaluationMode;
    }

    /**
     * How the policy is evaluated. {@code rest} (the default) calls a running OPA server over its Data API.
     * {@code wasm} evaluates a WebAssembly bundle in-process, with no server involved - so there is no network hop and
     * no unreachable decision point, at the cost of the policy being a build-time artefact rather than something a
     * server distributes and updates. {@code serverUrl}, {@code bearerToken} and {@code failOpen} do not apply in
     * {@code wasm} mode.
     */
    public void setEvaluationMode(String evaluationMode) {
        this.evaluationMode = evaluationMode;
    }

    public String getPolicyBundle() {
        return policyBundle;
    }

    /**
     * The WebAssembly policy to evaluate in {@code wasm} mode, as produced by {@code opa build -t wasm}. Accepts a
     * {@code file:}, {@code classpath:} or {@code http:} location holding either the {@code bundle.tar.gz} that
     * {@code opa build} emits or a bare {@code .wasm} module. Required when {@code evaluationMode=wasm}. Prefer the
     * bundle: it also carries the data document the policy reads as {@code data.*}, which a bare module does not.
     */
    public void setPolicyBundle(String policyBundle) {
        this.policyBundle = policyBundle;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    /**
     * The compiled entrypoint to evaluate in {@code wasm} mode. This is not the same thing as the policy path: an
     * entrypoint is fixed when the bundle is built, with {@code opa build -e}. Defaults to the endpoint's policy path,
     * which is the name {@code opa build} gives it.
     */
    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public int getPoolSize() {
        return poolSize;
    }

    /**
     * How many WebAssembly policy instances to pool in {@code wasm} mode. An instance carries mutable state and is not
     * thread-safe, so each exchange borrows one; this bounds how many exchanges evaluate at once.
     */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public long getBorrowTimeout() {
        return borrowTimeout;
    }

    /**
     * How long an exchange waits for a free WebAssembly policy instance in {@code wasm} mode before the evaluation
     * fails. An exchange that cannot get an instance is not denied by a policy, so it is reported as an evaluation
     * failure and handled like any other: failing closed, or proceeding if {@code failOpen} is set. Raise it, or
     * {@code poolSize}, for a route whose concurrency exceeds the pool.
     */
    public void setBorrowTimeout(long borrowTimeout) {
        this.borrowTimeout = borrowTimeout;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * How long to wait for the connection to the OPA server to be established, in {@code rest} mode. The SDK's own
     * transport applies no timeout at all, so a server that never answers would otherwise park the calling thread
     * indefinitely rather than letting the component fail closed.
     */
    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * How long to wait for the decision once connected, in {@code rest} mode. A request that times out is an evaluation
     * failure rather than a deny, so it fails closed - or proceeds when {@code failOpen} is set - like any other
     * failure to reach a verdict.
     */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * TLS configuration for the connection to the OPA server in {@code rest} mode. Needed to trust a server whose
     * certificate comes from a private CA, and to present a client certificate to a server that requires mutual TLS - a
     * SPIFFE X.509-SVID, for instance, so the workload authenticates to the policy decision point as itself.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
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
