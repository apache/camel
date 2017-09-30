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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClient;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.xnio.AbstractIoFuture;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

public final class UndertowComponentVerifierExtension extends DefaultComponentVerifierExtension {
    private ThreadLocal<WeakReference<UndertowClientWrapper>> clientCache;

    UndertowComponentVerifierExtension() {
        super("undertow");

        clientCache = new ThreadLocal<>();
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        // Default is success
        final ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS);
        // Make a copy to avoid clashing with parent validation
        final HashMap<String, Object> verifyParams = new HashMap<>(parameters);
        // Check if validation is rest-related
        final boolean isRest = verifyParams.entrySet().stream().anyMatch(e -> e.getKey().startsWith("rest."));

        if (isRest) {
            String httpUri = getOption(verifyParams, "rest.host", String.class).orElse(null);
            String path = getOption(verifyParams, "rest.path", String.class).map(FileUtil::stripLeadingSeparator).orElse(null);

            if (ObjectHelper.isNotEmpty(httpUri) && ObjectHelper.isNotEmpty(path)) {
                httpUri = httpUri + "/" + path;
            }

            verifyParams.put("httpURI", httpUri);

            // Cleanup parameters map from rest related stuffs
            verifyParams.entrySet().removeIf(e -> e.getKey().startsWith("rest."));
        }

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, verifyParams);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        // Default is success
        final ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);
        // Make a copy to avoid clashing with parent validation
        final HashMap<String, Object> verifyParams = new HashMap<>(parameters);
        // Check if validation is rest-related
        final boolean isRest = verifyParams.entrySet().stream().anyMatch(e -> e.getKey().startsWith("rest."));

        String httpUri = null;
        String httpMethod = null;

        if (isRest) {
            // We are doing rest endpoint validation but as today the endpoint
            // can't do any param substitution so the validation is performed
            // against the http uri
            httpUri = getOption(verifyParams, "rest.host", String.class).orElse(null);
            httpMethod = getOption(verifyParams, "rest.method", String.class).orElse(null);

            String path = getOption(verifyParams, "rest.path", String.class).map(FileUtil::stripLeadingSeparator).orElse(null);
            if (ObjectHelper.isNotEmpty(httpUri) && ObjectHelper.isNotEmpty(path)) {
                httpUri = httpUri + "/" + path;
            }

            verifyParams.put("httpURI", httpUri);

            // Cleanup parameters from rest related stuffs
            verifyParams.entrySet().removeIf(e -> e.getKey().startsWith("rest."));
        }

        final URI uri = getHttpUri(verifyParams);

        // Check whether the http uri is null or empty
        if (ObjectHelper.isEmpty(uri)) {
            builder.error(
                ResultErrorBuilder.withMissingOption("httpURI")
                    .detail("rest", isRest)
                    .build()
            );

            // lack of httpURI is a blocking issue so no need to go further
            // with the validation
            return builder.build();
        }

        try {
            final UndertowClientWrapper wrapper = getUndertowClientWrapper();
            final ClientResponse response = wrapper.send(uri, Optional.ofNullable(httpMethod));

            if (response != null) {
                int code = response.getResponseCode();
                if (code == 401) {
                    // Unauthorized, add authUsername and authPassword to the list
                    // of parameters in error
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(response.getStatus())
                            .build()
                    );
                } else if (code >= 300 && code < 400) {
                    // redirect
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(response.getStatus())
                            .parameterKey("httpURI")
                            .detail(
                                VerificationError.HttpAttribute.HTTP_REDIRECT,
                                () -> Optional.ofNullable(response.getResponseHeaders().get(Headers.LOCATION).getFirst()))
                            .build()
                    );
                } else if (code >= 400) {
                    // generic http error
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(response.getStatus())
                            .build()
                    );
                }
            }

        } catch (Exception e) {
            builder.error(
                ResultErrorBuilder.withException(e).build()
            );
        }

        return builder.build();
    }

    // *********************************
    // Helpers
    // *********************************

    private URI getHttpUri(Map<String, Object> parameters) {
        URI uri = null;

        // check if a client has been supplied to the parameters map
        for (Object value : parameters.values()) {
            if (value instanceof URI) {
                uri = URI.class.cast(value);
                break;
            }
        }

        if (ObjectHelper.isEmpty(uri)) {
            String httpUri = (String)parameters.get("httpURI");

            if (!ObjectHelper.isEmpty(httpUri)) {
                uri = URI.create(UnsafeUriCharactersEncoder.encodeHttpURI(httpUri));
            }
        }

        return uri;
    }

    private UndertowClientWrapper getUndertowClientWrapper() throws Exception {
        WeakReference<UndertowClientWrapper> ref = clientCache.get();
        UndertowClientWrapper wrapper = null;

        if (ref != null) {
            wrapper = ref.get();
        }

        if (wrapper == null) {
            wrapper = new UndertowClientWrapper();
            clientCache.set(new WeakReference<>(wrapper));
        }

        return wrapper;
    }

    private final class UndertowClientWrapper {
        private final XnioWorker worker;
        private final ByteBufferPool pool;
        private UndertowClient client;

        private UndertowClientWrapper() throws IOException, URISyntaxException {
            this.worker = Xnio.getInstance().createWorker(OptionMap.EMPTY);
            this.pool = new DefaultByteBufferPool(true, 17 * 1024);
            this.client = UndertowClient.getInstance();
        }

        public ClientResponse send(URI uri, Optional<String> httpMethod) throws Exception {
            HttpString method = httpMethod.map(String::toUpperCase).map(Methods::fromString).orElse(Methods.GET);

            ClientRequest request = new ClientRequest();
            request.setMethod(method);
            request.setPath(URISupport.pathAndQueryOf(uri));

            IoFuture<ClientConnection> connectFuture = client.connect(uri, worker, pool, OptionMap.EMPTY);
            UndertowClientResponseFuture responseFuture = new UndertowClientResponseFuture();

            connectFuture.get().sendRequest(request, responseFuture);

            // We should set a timeout
            return responseFuture.get().getResponse();
        }
    }

    private final class UndertowClientResponseFuture extends AbstractIoFuture<ClientExchange> implements ClientCallback<ClientExchange> {
        @Override
        public void completed(ClientExchange result) {
            result.setResponseListener(new ClientCallback<ClientExchange>() {
                @Override
                public void completed(ClientExchange result) {
                    setResult(result);
                }
                @Override
                public void failed(IOException e) {
                    setException(e);
                }
            });
        }

        @Override
        public void failed(IOException e) {
            setException(e);
        }
    }
}
