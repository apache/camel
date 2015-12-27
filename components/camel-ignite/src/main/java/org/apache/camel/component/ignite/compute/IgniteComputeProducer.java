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
package org.apache.camel.component.ignite.compute;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.MessageHelper;
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
            switch (executionTypeFor(exchange)) {
            
            case CALL:
                doCall(exchange, callback, compute);
                break;
                
            case BROADCAST:
                doBroadcast(exchange, callback, compute);
                break;
                
            case EXECUTE:
                doExecute(exchange, callback, compute);
                break;
                
            case RUN:
                doRun(exchange, callback, compute);
                break;
                
            case APPLY:
                doApply(exchange, callback, compute);
                break;
                
            case AFFINITY_CALL:
                doAffinityCall(exchange, callback, compute);
                break;
                
            case AFFINITY_RUN:
                doAffinityRun(exchange, callback, compute);
                break;
                
            default:
                exchange.setException(new UnsupportedOperationException("Operation not supported by Ignite Compute producer."));
                return true;
            }

            compute.future().listen(IgniteInCamelClosure.create(exchange, callback));

        } catch (Exception e) {
            exchange.setException(e);
            return true;
        }

        return false;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void doCall(final Exchange exchange, final AsyncCallback callback, IgniteCompute compute) throws Exception {
        Object job = exchange.getIn().getBody();
        IgniteReducer<Object, Object> reducer = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_REDUCER, IgniteReducer.class);

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
            throw new RuntimeCamelException(String.format(
                    "Ignite Compute endpoint with CALL executionType is only " + "supported for IgniteCallable payloads, or collections of them. The payload type was: %s.", job.getClass().getName()));
        }
    }

    @SuppressWarnings("unchecked")
    private void doBroadcast(final Exchange exchange, final AsyncCallback callback, IgniteCompute compute) throws Exception {
        Object job = exchange.getIn().getBody();

        if (IgniteCallable.class.isAssignableFrom(job.getClass())) {
            compute.broadcast((IgniteCallable<?>) job);
        } else if (IgniteRunnable.class.isAssignableFrom(job.getClass())) {
            compute.broadcast((IgniteRunnable) job);
        } else if (IgniteClosure.class.isAssignableFrom(job.getClass())) {
            compute.broadcast((IgniteClosure<Object, Object>) job, exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_PARAMS));
        } else {
            throw new RuntimeCamelException(
                    String.format("Ignite Compute endpoint with BROADCAST executionType is only " + "supported for IgniteCallable, IgniteRunnable or IgniteClosure payloads. The payload type was: %s.",
                            job.getClass().getName()));
        }
    }

    @SuppressWarnings("unchecked")
    private void doExecute(final Exchange exchange, final AsyncCallback callback, IgniteCompute compute) throws Exception {
        Object job = exchange.getIn().getBody();
        Object params = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_PARAMS);

        if (job instanceof Class && ComputeTask.class.isAssignableFrom((Class<?>) job)) {
            Class<? extends ComputeTask<Object, Object>> task = (Class<? extends ComputeTask<Object, Object>>) job;
            compute.execute(task, params);
        } else if (ComputeTask.class.isAssignableFrom(job.getClass())) {
            compute.execute((ComputeTask<Object, Object>) job, params);
        } else if (endpoint.getTaskName() != null) {
            if (exchange.getIn().getBody() != null) {
                params = exchange.getIn().getBody();
            }
            compute.execute(endpoint.getTaskName(), params);
        } else {
            throw new RuntimeCamelException(String.format("Ignite Compute endpoint with EXECUTE executionType is only "
                    + "supported for ComputeTask payloads, Class<ComputeTask> or any payload in conjunction with the " + "task name option. The payload type was: %s.", job.getClass().getName()));
        }
    }

    private void doRun(final Exchange exchange, final AsyncCallback callback, IgniteCompute compute) throws Exception {
        Object job = exchange.getIn().getBody();

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
            throw new RuntimeCamelException(String.format(
                    "Ignite Compute endpoint with RUN executionType is only " + "supported for IgniteRunnable payloads, or collections of them. The payload type was: %s.", job.getClass().getName()));
        }
    }

    @SuppressWarnings("unchecked")
    private  <T, R1, R2> void doApply(final Exchange exchange, final AsyncCallback callback, IgniteCompute compute) throws Exception {
        IgniteClosure<T, R1> job = exchange.getIn().getBody(IgniteClosure.class);
        T params = (T) exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_PARAMS);

        if (job == null || params == null) {
            throw new RuntimeCamelException(
                    String.format("Ignite Compute endpoint with APPLY executionType is only " + "supported for IgniteClosure payloads with parameters. The payload type was: %s.",
                            exchange.getIn().getBody().getClass().getName()));
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
    private void doAffinityCall(final Exchange exchange, final AsyncCallback callback, IgniteCompute compute) throws Exception {
        IgniteCallable<Object> job = exchange.getIn().getBody(IgniteCallable.class);
        String affinityCache = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_AFFINITY_CACHE_NAME, String.class);
        Object affinityKey = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_AFFINITY_KEY, Object.class);

        if (job == null || affinityCache == null || affinityKey == null) {
            throw new RuntimeCamelException(String.format(
                    "Ignite Compute endpoint with AFFINITY_CALL executionType is only " + "supported for IgniteCallable payloads, along with an affinity cache and key. The payload type was: %s.",
                    exchange.getIn().getBody().getClass().getName()));
        }

        compute.affinityCall(affinityCache, affinityKey, job);
    }

    private void doAffinityRun(final Exchange exchange, final AsyncCallback callback, IgniteCompute compute) throws Exception {
        IgniteRunnable job = exchange.getIn().getBody(IgniteRunnable.class);
        String affinityCache = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_AFFINITY_CACHE_NAME, String.class);
        Object affinityKey = exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_AFFINITY_KEY, Object.class);

        if (job == null || affinityCache == null || affinityKey == null) {
            throw new RuntimeCamelException(String.format(
                    "Ignite Compute endpoint with AFFINITY_RUN executionType is only " + "supported for IgniteRunnable payloads, along with an affinity cache and key. The payload type was: %s.",
                    exchange.getIn().getBody().getClass().getName()));
        }

        compute.affinityRun(affinityCache, affinityKey, job);
    }

    private IgniteComputeExecutionType executionTypeFor(Exchange exchange) {
        return exchange.getIn().getHeader(IgniteConstants.IGNITE_COMPUTE_EXECUTION_TYPE, endpoint.getExecutionType(), IgniteComputeExecutionType.class);
    }

    private static class IgniteInCamelClosure implements IgniteInClosure<IgniteFuture<Object>> {
        private static final long serialVersionUID = 7486030906412223384L;

        private Exchange exchange;
        private AsyncCallback callback;

        private static IgniteInCamelClosure create(Exchange exchange, AsyncCallback callback) {
            IgniteInCamelClosure answer = new IgniteInCamelClosure();
            answer.exchange = exchange;
            answer.callback = callback;
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

            exchange.getOut().setBody(result);
            callback.done(false);
        }
    };

}
