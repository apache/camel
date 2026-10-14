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
package org.apache.camel.service.lra;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.*;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel saga service based on Microprofile LRA (https://github.com/eclipse/microprofile-lra).
 */
@JdkService("lra-saga-service")
@Configurer
@ManagedResource(description = "Managed LRASagaService")
public class LRASagaService extends ServiceSupport implements StaticService, CamelSagaService {

    private static final Logger LOG = LoggerFactory.getLogger(LRASagaService.class);
    private static final List<String> RESERVED_OPTION_NAMES = List.of(
            LRAConstants.URL_COMPENSATION_KEY, LRAConstants.URL_COMPLETION_KEY, LRAConstants.URL_SIGNATURE_KEY,
            LRAConstants.URL_LRA_KEY);

    private CamelContext camelContext;
    private ScheduledExecutorService executorService;
    private LRAClient client;
    private LRASagaRoutes routes;
    private final Set<String> sagaURIs = ConcurrentHashMap.newKeySet();

    // we want to be able to configure these following options
    @Metadata
    private String coordinatorUrl;
    @Metadata
    private String coordinatorContextPath = LRAConstants.DEFAULT_COORDINATOR_CONTEXT_PATH;
    @Metadata
    private String localParticipantUrl;
    @Metadata
    private String localParticipantContextPath = LRAConstants.DEFAULT_LOCAL_PARTICIPANT_CONTEXT_PATH;
    @Metadata(label = "security")
    private String allowedCoordinatorUrls;
    @Metadata(label = "security", secret = true)
    private String callbackSecret;
    private LRACallbackSigner callbackSigner;

    public LRASagaService() {
    }

    @Override
    public CompletableFuture<CamelSagaCoordinator> newSaga(Exchange exchange) {
        return client.newLRA(exchange)
                .thenApply(url -> new LRASagaCoordinator(LRASagaService.this, url));
    }

    /**
     * The LRA protocol carries the coordinator id in the {@code Long-Running-Action} header, so a saga started by
     * another participant is joined through it.
     */
    @Override
    public boolean isLongRunningActionHeaderSupported() {
        return true;
    }

    /**
     * Returns the coordinator of an LRA, which is joined, completed or compensated by calling its URL. Only an LRA
     * hosted by the configured coordinator, or by one of the allowed coordinator URLs, is accepted.
     */
    @Override
    public CompletableFuture<CamelSagaCoordinator> getSaga(String id) {
        CompletableFuture<CamelSagaCoordinator> coordinator;
        try {
            URI lra = URI.create(id);
            if (!isHostedByAllowedCoordinator(lra)) {
                throw new IllegalArgumentException(
                        "The Long-Running-Action " + id + " is not hosted by the LRA coordinator configured on the LRA"
                                                   + " saga service. If it is a trusted coordinator, add its URL to"
                                                   + " allowedCoordinatorUrls.");
            }
            coordinator = CompletableFuture.completedFuture(new LRASagaCoordinator(this, lra.toURL()));
        } catch (Exception ex) {
            coordinator = CompletableFuture.failedFuture(ex);
        }
        return coordinator;
    }

    private boolean isHostedByAllowedCoordinator(URI lra) {
        for (URI coordinator : allowedCoordinators()) {
            if (isHostedBy(lra, coordinator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The URLs of the coordinators whose LRAs are accepted: the configured coordinator, and the allowed coordinator
     * URLs.
     */
    private List<URI> allowedCoordinators() {
        List<URI> coordinators = new ArrayList<>();
        coordinators.add(URI.create(new LRAUrlBuilder().host(coordinatorUrl).path(coordinatorContextPath).build()));
        coordinators.addAll(additionalCoordinators());
        return coordinators;
    }

    private List<URI> additionalCoordinators() {
        List<URI> coordinators = new ArrayList<>();
        if (ObjectHelper.isNotEmpty(allowedCoordinatorUrls)) {
            for (String url : allowedCoordinatorUrls.split(",")) {
                if (!url.isBlank()) {
                    coordinators.add(URI.create(url.trim()));
                }
            }
        }
        return coordinators;
    }

    /**
     * Whether the LRA URL is under the coordinator URL: same scheme, host and port, no user info, and a path under the
     * path of the coordinator.
     */
    private static boolean isHostedBy(URI lra, URI coordinator) {
        // host and port are read through URL, which also takes host names that URI does not, such as lra_coordinator
        URL lraUrl = toUrl(lra);
        URL coordinatorUrl = toUrl(coordinator);
        if (lraUrl == null || coordinatorUrl == null || lraUrl.getUserInfo() != null || lraUrl.getHost().isEmpty()) {
            return false;
        }
        String rawPath = lra.getRawPath();
        if (rawPath == null || hasEncodedOrParameterSegment(rawPath)) {
            return false;
        }
        String lraPath = lra.normalize().getRawPath();
        String coordinatorPath = Objects.toString(coordinator.normalize().getRawPath(), "");
        if (!coordinatorPath.endsWith("/")) {
            coordinatorPath += "/";
        }
        return lraUrl.getProtocol().equalsIgnoreCase(coordinatorUrl.getProtocol())
                && lraUrl.getHost().equalsIgnoreCase(coordinatorUrl.getHost())
                && port(lraUrl) == port(coordinatorUrl)
                && lraPath.startsWith(coordinatorPath);
    }

    /**
     * Whether the path holds a path parameter, or an encoded dot, slash or backslash. A server may resolve them as dot
     * segments after this check, so such a path could leave the path of the coordinator.
     */
    private static boolean hasEncodedOrParameterSegment(String rawPath) {
        String path = rawPath.toLowerCase(Locale.ROOT);
        return path.contains(";") || path.contains("%2e") || path.contains("%2f") || path.contains("%5c");
    }

    private static URL toUrl(URI uri) {
        try {
            return uri.isAbsolute() ? uri.toURL() : null;
        } catch (MalformedURLException | IllegalArgumentException e) {
            return null;
        }
    }

    private static int port(URL url) {
        return url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
    }

    @Override
    public void registerStep(CamelSagaStep step) {
        checkOptionNames(step);
        // Register which uris should be exposed
        step.getCompensation().map(Endpoint::getEndpointUri).map(this.sagaURIs::add);
        step.getCompletion().map(Endpoint::getEndpointUri).map(this.sagaURIs::add);
    }

    /**
     * The saga options travel in the query of the participant callback URLs, next to the parameters of the saga
     * service, so an option cannot use one of their names. Header names are case-insensitive, so neither can it use
     * them in another case.
     */
    private static void checkOptionNames(CamelSagaStep step) {
        if (step.getOptions() == null) {
            return;
        }
        for (String name : step.getOptions().keySet()) {
            for (String reserved : RESERVED_OPTION_NAMES) {
                if (reserved.equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException(
                            "The saga option " + name + " uses a name reserved by the LRA saga service");
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        for (URI coordinator : additionalCoordinators()) {
            URL url = toUrl(coordinator);
            if (url == null || url.getHost().isEmpty()
                    || !("http".equals(url.getProtocol()) || "https".equals(url.getProtocol()))) {
                throw new IllegalArgumentException(
                        "allowedCoordinatorUrls must hold http or https coordinator URLs with a host name, but no such"
                                                   + " URL could be read from: " + coordinator);
            }
        }
        if (callbackSecret != null && callbackSecret.isBlank()) {
            // for example an empty environment variable, which would otherwise switch the verification off
            throw new IllegalArgumentException(
                    "callbackSecret is set on the LRA saga service but blank: set a secret, or remove the option");
        }
        if (callbackSigner == null) {
            LOG.warn("Callbacks to the LRA participant endpoints under {} are not signed. Configure a callbackSecret"
                     + " on the LRA saga service, or make sure that only the LRA coordinator can reach these endpoints.",
                    localParticipantContextPath);
        }
        if (this.executorService == null) {
            this.executorService = camelContext.getExecutorServiceManager()
                    .newDefaultScheduledThreadPool(this, "saga-lra");
        }
        if (this.client == null) {
            this.client = createLRAClient();
        }
    }

    /**
     * Use this method to override some behavior within the LRAClient
     *
     * @return the LRAClient to be used within the LRASagaService
     *
     */
    protected LRAClient createLRAClient() {
        return new LRAClient(this);
    }

    @Override
    protected void doStop() throws Exception {
        if (this.executorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(this.executorService);
            this.executorService = null;
        }
        if (this.client != null) {
            this.client.close();
            this.client = null;
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        if (this.routes == null) {
            this.routes = new LRASagaRoutes(this);
            try {
                this.camelContext.addRoutes(this.routes);
            } catch (Exception ex) {
                throw RuntimeCamelException.wrapRuntimeException(ex);
            }
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public LRAClient getClient() {
        return client;
    }

    @ManagedAttribute(description = "Coordinator URL")
    public String getCoordinatorUrl() {
        return coordinatorUrl;
    }

    public void setCoordinatorUrl(String coordinatorUrl) {
        this.coordinatorUrl = coordinatorUrl;
    }

    @ManagedAttribute(description = "Coordinator context-path")
    public String getCoordinatorContextPath() {
        return coordinatorContextPath;
    }

    public void setCoordinatorContextPath(String coordinatorContextPath) {
        this.coordinatorContextPath = coordinatorContextPath;
    }

    @ManagedAttribute(description = "Local participant URL")
    public String getLocalParticipantUrl() {
        return localParticipantUrl;
    }

    public void setLocalParticipantUrl(String localParticipantUrl) {
        this.localParticipantUrl = localParticipantUrl;
    }

    @ManagedAttribute(description = "Local participant context-path")
    public String getLocalParticipantContextPath() {
        return localParticipantContextPath;
    }

    public void setLocalParticipantContextPath(String localParticipantContextPath) {
        this.localParticipantContextPath = localParticipantContextPath;
    }

    @ManagedAttribute(description = "Allowed coordinator URLs")
    public String getAllowedCoordinatorUrls() {
        return allowedCoordinatorUrls;
    }

    /**
     * Comma-separated URLs, including the context path, of further LRA coordinators whose LRAs may be joined, completed
     * or compensated. By default, only an LRA hosted by the configured coordinator is accepted. Use it when
     * participants reach the same coordinator under different URLs, for example with different host names.
     */
    public void setAllowedCoordinatorUrls(String allowedCoordinatorUrls) {
        this.allowedCoordinatorUrls = allowedCoordinatorUrls;
    }

    public String getCallbackSecret() {
        return callbackSecret;
    }

    /**
     * Secret used to sign the participant callback URLs that are enlisted with the LRA coordinator, and to verify them
     * when the coordinator calls back. When set, a compensation or completion callback with a missing or invalid
     * signature is rejected with HTTP 403. All the instances of a participant must use the same secret.
     */
    public void setCallbackSecret(String callbackSecret) {
        this.callbackSecret = callbackSecret;
        this.callbackSigner = ObjectHelper.isNotEmpty(callbackSecret) ? new LRACallbackSigner(callbackSecret) : null;
    }

    LRACallbackSigner getCallbackSigner() {
        return callbackSigner;
    }

    public Set<String> getRegisteredURIs() {
        return sagaURIs;
    }

    @Override
    public String toString() {
        return "lra-saga-service";
    }
}
