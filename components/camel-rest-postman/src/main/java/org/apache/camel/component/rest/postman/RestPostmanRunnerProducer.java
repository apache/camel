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
package org.apache.camel.component.rest.postman;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs every request of a folder or of a whole collection, in the order they appear in the collection.
 * <p>
 * This is the equivalent of Postman's collection runner. Unlike the single request producer, the body and headers sent
 * are the ones written in the collection, because one exchange body cannot stand in for many different requests. The
 * message body of the exchange becomes a {@code List} of {@link PostmanRunResult}.
 */
public class RestPostmanRunnerProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RestPostmanRunnerProducer.class);

    private final List<PreparedRequest> requests;
    private final boolean failFast;
    private final String description;

    public RestPostmanRunnerProducer(Endpoint endpoint, List<PreparedRequest> requests, boolean failFast,
                                     String description) {
        super(endpoint);
        this.requests = List.copyOf(requests);
        this.failFast = failFast;
        this.description = description;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        List<PostmanRunResult> results = new ArrayList<>(requests.size());
        int failed = 0;

        for (PreparedRequest prepared : requests) {
            Exchange sub = prepared.producer().getEndpoint().createExchange(ExchangePattern.InOut);
            prepareRequest(sub, prepared.binding());

            Exception failure = null;
            try {
                prepared.producer().process(sub);
                failure = sub.getException();
            } catch (Exception e) {
                failure = e;
            }

            results.add(toResult(prepared.binding(), sub, failure));
            releaseUnitOfWork(sub);

            if (failure != null) {
                failed++;
                if (failFast) {
                    throw new RuntimeCamelException(
                            "Postman request " + prepared.binding().item().describe() + " failed while running "
                                                    + description + ". Set runFailFast=false to run the remaining "
                                                    + (requests.size() - results.size()) + " request(s) anyway.",
                            failure);
                }
                LOG.debug("Postman request {} failed while running {}", prepared.binding().id(), description, failure);
            }
        }

        Message out = exchange.getMessage();
        out.setBody(results);
        out.setHeader(RestPostmanConstants.REQUEST_COUNT, results.size());
        out.setHeader(RestPostmanConstants.FAILED_COUNT, failed);
    }

    /**
     * Completes the unit of work of a sub-exchange, if it has one.
     * <p>
     * A sub-exchange created straight from the delegate endpoint and handed to a producer normally carries no unit of
     * work, because nothing in that path starts one. Releasing it when present keeps the cleanup deterministic rather
     * than leaving any registered synchronization to garbage collection, which matters when a run covers a large
     * collection.
     */
    private static void releaseUnitOfWork(Exchange sub) {
        UnitOfWork uow = sub.getUnitOfWork();
        if (uow != null) {
            UnitOfWorkHelper.doneUow(uow, sub);
        }
    }

    /**
     * Populates a sub-exchange with what the collection says this request should send.
     */
    private static void prepareRequest(Exchange sub, PostmanRequestBinding binding) {
        Message in = sub.getMessage();
        in.setBody(binding.collectionBody());

        binding.staticHeaders().forEach((key, value) -> {
            if (value != null) {
                in.setHeader(key, value);
            }
        });
        binding.defaultPathValues().forEach(in::setHeader);

        if (binding.produces() != null && binding.collectionBody() != null) {
            in.setHeader(Exchange.CONTENT_TYPE, binding.produces());
        }
        if (binding.consumes() != null) {
            in.setHeader("Accept", binding.consumes());
        }

        in.setHeader(RestPostmanConstants.REQUEST_ID, binding.id());
        in.setHeader(RestPostmanConstants.REQUEST_NAME, binding.item().getName());
        if (!binding.item().getFolderPath().isEmpty()) {
            in.setHeader(RestPostmanConstants.FOLDER_PATH, String.join("/", binding.item().getFolderPath()));
        }
    }

    private static PostmanRunResult toResult(PostmanRequestBinding binding, Exchange sub, Exception failure) {
        Message out = sub.getMessage();
        Map<String, Object> headers = new LinkedHashMap<>(out.getHeaders());

        Integer status = null;
        Object code = headers.get(Exchange.HTTP_RESPONSE_CODE);
        if (code instanceof Number n) {
            status = n.intValue();
        }

        String folderPath = binding.item().getFolderPath().isEmpty()
                ? null : String.join("/", binding.item().getFolderPath());

        return new PostmanRunResult(
                binding.id(),
                binding.item().getName(),
                folderPath,
                binding.method(),
                (binding.host() != null ? binding.host() : "") + binding.fullPath(),
                status,
                failure == null ? out.getBody() : null,
                headers,
                failure != null ? failure.getMessage() : null);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        for (PreparedRequest prepared : requests) {
            ServiceHelper.startService(prepared.producer());
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (PreparedRequest prepared : requests) {
            ServiceHelper.stopService(prepared.producer());
        }
        super.doStop();
    }

    /**
     * A request of the collection together with the delegate producer that calls it.
     */
    public record PreparedRequest(PostmanRequestBinding binding, Producer producer) {
    }
}
