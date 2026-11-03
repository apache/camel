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
package org.apache.camel.component.ignite.compute;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.ignite.IgniteCompute;
import org.apache.ignite.compute.ComputeTask;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteClosure;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteInClosure;
import org.apache.ignite.lang.IgniteReducer;
import org.apache.ignite.lang.IgniteRunnable;

/**
 * Ignite Compute producer.
 */
public class IgniteComputeProducer extends DefaultAsyncProducer {

    private IgniteComputeEndpoint endpoint;

    public IgniteComputeProducer(IgniteComputeEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        IgniteCompute compute = endpoint.createIgniteCompute().withAsync();

        try {
            IgniteComputeExecutionType executionType = executionTypeFor(exchange);
            switch (executionType) {

                case CALL:
                    doCall(exchange, compute);
                    break;

                case BROADCAST:
                    doBroadcast(exchange, compute);
                    break;

                case EXECUTE:
                    doExecute(exchange, compute);
                    break;

                case RUN:
                    doRun(exchange, compute);
                    break;

                case APPLY:
                    doApply(exchange, compute);
                    break;

                case AFFINITY_CALL:
                    doAffinityCall(exchange, compute);
                    break;

                case AFFINITY_RUN:
                    doAffinityRun(exchange, compute);
                    break;

                default:
                    exchange.setException(
                            new UnsupportedOperationException("Operation not supported by Ignite Compute producer."));
                    callback.done(false);
                    return false;
            }

            // RUN and AFFINITY_RUN have no result, so they keep the incoming body unless told otherwise
            boolean propagateIncomingBody = (executionType == IgniteComputeExecutionType.RUN
                    || executionType == IgniteComputeExecutionType.AFFINITY_RUN)
                    && endpoint.isPropagateIncomingBodyIfNoReturnValue();
            compute.future().listen(IgniteInCamelClosure.create(exchange, callback, propagateIncomingBody));
            return false;

        } catch (Exception e) {
            exchange.setException(e);
            callback.done(false);
            return false;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void doCall(final Exchange exchange, IgniteCompute compute)
            throws NoTypeConversionAvailableException, InvalidPayloadException {
        Object job = mandatoryJob(exchange, IgniteComputeExecutionType.CALL, "an IgniteCallable, or a Collection of them");
        IgniteReducer<Object, Object> reducer
                = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_REDUCER, IgniteReducer.class);

        if (Collection.class.isAssignableFrom(job.getClass())) {
            Collection<?> col = (Collection<?>) job;
            TypeConverter tc = exchange.getContext().getTypeConverter();
            Collection<IgniteCallable<?>> callables = new ArrayList<>(col.size());
            for (Object o : col) {
                callables.add(tc.mandatoryConvertTo(IgniteCallable.class, o));
            }
            if (reducer != null) {
                compute.call((Collection) callables, reducer);
            } else {
                compute.call((Collection) callables);
            }
        } else if (IgniteCallable.class.isAssignableFrom(job.getClass())) {
            compute.call((IgniteCallable<Object>) job);
        } else {
            throw new RuntimeCamelException(
                    String.format(
                            "Ignite Compute endpoint with CALL executionType is only "
                                  + "supported for IgniteCallable payloads, or collections of them. The payload type was: %s.",
                            job.getClass().getName()));
        }
    }

    @SuppressWarnings("unchecked")
    private void doBroadcast(final Exchange exchange, IgniteCompute compute) throws InvalidPayloadException {
        Object job = mandatoryJob(exchange, IgniteComputeExecutionType.BROADCAST,
                "an IgniteCallable, an IgniteRunnable or an IgniteClosure");

        if (IgniteCallable.class.isAssignableFrom(job.getClass())) {
            compute.broadcast((IgniteCallable<?>) job);
        } else if (IgniteRunnable.class.isAssignableFrom(job.getClass())) {
            compute.broadcast((IgniteRunnable) job);
        } else if (IgniteClosure.class.isAssignableFrom(job.getClass())) {
            compute.broadcast((IgniteClosure<Object, Object>) job,
                    exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_PARAMS));
        } else {
            throw new RuntimeCamelException(
                    String.format("Ignite Compute endpoint with BROADCAST executionType is only "
                                  + "supported for IgniteCallable, IgniteRunnable or IgniteClosure payloads. The payload type was: %s.",
                            job.getClass().getName()));
        }
    }

    @SuppressWarnings("unchecked")
    private void doExecute(final Exchange exchange, IgniteCompute compute) throws InvalidPayloadException {
        Object job = exchange.getIn().getBody();
        Object params = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_PARAMS);

        if (job instanceof Class && ComputeTask.class.isAssignableFrom((Class<?>) job)) {
            Class<? extends ComputeTask<Object, Object>> task = (Class<? extends ComputeTask<Object, Object>>) job;
            compute.execute(task, params);
        } else if (job instanceof ComputeTask) {
            compute.execute((ComputeTask<Object, Object>) job, params);
        } else if (endpoint.getTaskName() != null) {
            // the body is the argument of the task; without a body the CamelIgniteComputeParameters header is
            if (job != null) {
                params = job;
            }
            compute.execute(endpoint.getTaskName(), params);
        } else if (job == null) {
            throw new InvalidPayloadException(
                    exchange, Object.class, exchange.getIn(),
                    "the EXECUTE executionType needs a ComputeTask, or its Class, in the message body, or the taskName option");
        } else {
            throw new RuntimeCamelException(
                    String.format("Ignite Compute endpoint with EXECUTE executionType is only "
                                  + "supported for ComputeTask payloads, Class<ComputeTask> or any payload in conjunction with the "
                                  + "task name option. The payload type was: %s.",
                            job.getClass().getName()));
        }
    }

    private void doRun(final Exchange exchange, IgniteCompute compute)
            throws NoTypeConversionAvailableException, InvalidPayloadException {
        Object job = mandatoryJob(exchange, IgniteComputeExecutionType.RUN, "an IgniteRunnable, or a Collection of them");

        if (Collection.class.isAssignableFrom(job.getClass())) {
            Collection<?> col = (Collection<?>) job;
            TypeConverter tc = exchange.getContext().getTypeConverter();
            Collection<IgniteRunnable> runnables = new ArrayList<>(col.size());
            for (Object o : col) {
                runnables.add(tc.mandatoryConvertTo(IgniteRunnable.class, o));
            }
            compute.run(runnables);
        } else if (IgniteRunnable.class.isAssignableFrom(job.getClass())) {
            compute.run((IgniteRunnable) job);
        } else {
            throw new RuntimeCamelException(
                    String.format(
                            "Ignite Compute endpoint with RUN executionType is only "
                                  + "supported for IgniteRunnable payloads, or collections of them. The payload type was: %s.",
                            job.getClass().getName()));
        }
    }

    @SuppressWarnings("unchecked")
    private <T, R1, R2> void doApply(final Exchange exchange, IgniteCompute compute) throws InvalidPayloadException {
        Object body = mandatoryJob(exchange, IgniteComputeExecutionType.APPLY, "an IgniteClosure");
        IgniteClosure<T, R1> job = exchange.getIn().getBody(IgniteClosure.class);
        T params = (T) exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_PARAMS);

        if (job == null || params == null) {
            throw new RuntimeCamelException(
                    String.format("Ignite Compute endpoint with APPLY executionType is only "
                                  + "supported for IgniteClosure payloads with parameters. The payload type was: %s.",
                            body.getClass().getName()));
        }

        IgniteReducer<R1, R2> reducer = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_REDUCER, IgniteReducer.class);

        if (Collection.class.isAssignableFrom(params.getClass())) {
            Collection<T> colParams = (Collection<T>) params;
            if (reducer == null) {
                compute.apply(job, colParams);
            } else {
                compute.apply(job, colParams, reducer);
            }
        } else {
            compute.apply(job, params);
        }
    }

    @SuppressWarnings("unchecked")
    private void doAffinityCall(final Exchange exchange, IgniteCompute compute) throws InvalidPayloadException {
        Object body = mandatoryJob(exchange, IgniteComputeExecutionType.AFFINITY_CALL, "an IgniteCallable");
        IgniteCallable<Object> job = exchange.getIn().getBody(IgniteCallable.class);
        String affinityCache = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_AFFINITY_CACHE_NAME, String.class);
        Object affinityKey = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_AFFINITY_KEY, Object.class);

        if (job == null || affinityCache == null || affinityKey == null) {
            throw new RuntimeCamelException(
                    String.format(
                            "Ignite Compute endpoint with AFFINITY_CALL executionType is only "
                                  + "supported for IgniteCallable payloads, along with an affinity cache and key. The payload type was: %s.",
                            body.getClass().getName()));
        }

        compute.affinityCall(affinityCache, affinityKey, job);
    }

    private void doAffinityRun(final Exchange exchange, IgniteCompute compute) throws InvalidPayloadException {
        Object body = mandatoryJob(exchange, IgniteComputeExecutionType.AFFINITY_RUN, "an IgniteRunnable");
        IgniteRunnable job = exchange.getIn().getBody(IgniteRunnable.class);
        String affinityCache = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_AFFINITY_CACHE_NAME, String.class);
        Object affinityKey = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_AFFINITY_KEY, Object.class);

        if (job == null || affinityCache == null || affinityKey == null) {
            throw new RuntimeCamelException(
                    String.format(
                            "Ignite Compute endpoint with AFFINITY_RUN executionType is only "
                                  + "supported for IgniteRunnable payloads, along with an affinity cache and key. The payload type was: %s.",
                            body.getClass().getName()));
        }

        compute.affinityRun(affinityCache, affinityKey, job);
    }

    /**
     * The job in the body, or an {@link InvalidPayloadException} that says what the execution type needs when there is
     * no body.
     */
    private static Object mandatoryJob(Exchange exchange, IgniteComputeExecutionType executionType, String job)
            throws InvalidPayloadException {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            throw new InvalidPayloadException(
                    exchange, Object.class, exchange.getIn(),
                    "the " + executionType + " executionType needs " + job + " in the message body");
        }
        return body;
    }

    private IgniteComputeExecutionType executionTypeFor(Exchange exchange) {
        return exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_EXECUTION_TYPE, endpoint.getExecutionType(),
                IgniteComputeExecutionType.class);
    }

    private static class IgniteInCamelClosure implements IgniteInClosure<IgniteFuture<Object>> {
        private static final long serialVersionUID = 7486030906412223384L;

        private Exchange exchange;
        private AsyncCallback callback;
        private boolean propagateIncomingBody;

        private static IgniteInCamelClosure create(Exchange exchange, AsyncCallback callback, boolean propagateIncomingBody) {
            IgniteInCamelClosure answer = new IgniteInCamelClosure();
            answer.exchange = exchange;
            answer.callback = callback;
            answer.propagateIncomingBody = propagateIncomingBody;
            return answer;
        }

        @Override
        public void apply(IgniteFuture<Object> future) {
            Message in = exchange.getIn();
            Message out = exchange.getOut();
            MessageHelper.copyHeaders(in, out, true);

            Object result = null;

            try {
                result = future.get();
            } catch (Exception e) {
                exchange.setException(e);
                callback.done(false);
                return;
            }

            // a job without a result keeps the incoming body when propagateIncomingBodyIfNoReturnValue is true
            out.setBody(propagateIncomingBody ? in.getBody() : result);
            callback.done(false);
        }
    }

}
