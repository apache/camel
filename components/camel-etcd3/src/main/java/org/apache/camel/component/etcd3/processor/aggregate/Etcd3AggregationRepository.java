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
package org.apache.camel.component.etcd3.processor.aggregate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Txn;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Etcd3AggregationRepository extends ServiceSupport
        implements RecoverableAggregationRepository, OptimisticLockingAggregationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(Etcd3AggregationRepository.class);
    private static final String COMPLETED_SUFFIX = "-completed";

    private boolean optimistic;
    private boolean useRecovery = true;
    private String endpoint;
    private Client client;
    private boolean shutdownClient;
    private KV kvClient;
    private String prefixName;
    private String persistencePrefixName;
    private String deadLetterChannel;
    private long recoveryInterval = 5000;
    private int maximumRedeliveries = 3;
    private boolean allowSerializedHeaders;

    public Etcd3AggregationRepository() {
    }

    public Etcd3AggregationRepository(final String prefixName, final String endpoint) {
        this.prefixName = prefixName;
        this.persistencePrefixName = String.format("%s%s", prefixName, COMPLETED_SUFFIX);
        this.optimistic = false;
        this.endpoint = endpoint;
    }

    public Etcd3AggregationRepository(final String prefixName, final String persistencePrefixName,
                                      final String endpoint) {
        this.prefixName = prefixName;
        this.persistencePrefixName = persistencePrefixName;
        this.optimistic = false;
        this.endpoint = endpoint;
    }

    public Etcd3AggregationRepository(final String prefixName, final String endpoint, boolean optimistic) {
        this(prefixName, endpoint);
        this.optimistic = optimistic;
    }

    public Etcd3AggregationRepository(final String repositoryName, final String persistentRepositoryName,
                                      final String endpoint, boolean optimistic) {
        this(repositoryName, persistentRepositoryName, endpoint);
        this.optimistic = optimistic;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange)
            throws OptimisticLockingException {
        if (!optimistic) {
            throw new UnsupportedOperationException();
        }
        LOG.trace("Adding an Exchange with ID {} for key {} in an optimistic manner.", newExchange.getExchangeId(),
                key);
        try {
            if (oldExchange == null) {
                DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(newExchange, true, allowSerializedHeaders);
                CompletableFuture<GetResponse> completableGetResponse = kvClient
                        .get(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()));
                GetResponse getResponse = completableGetResponse.get();
                List<KeyValue> keyValues = getResponse.getKvs();
                if (keyValues.isEmpty()) {
                    CompletableFuture<PutResponse> completablePutResponse = kvClient.put(
                            ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()),
                            convertToEtcd3Format(holder));
                    completablePutResponse.get();
                } else {
                    DefaultExchangeHolder misbehaviorHolder
                            = (DefaultExchangeHolder) convertFromEtcd3Format(keyValues.get(0).getValue());
                    Exchange misbehaviorEx = unmarshallExchange(camelContext, misbehaviorHolder);
                    LOG.warn(
                            "Optimistic locking failed for exchange with key {}: kvClient.get returned Exchange with ID {}, while it's expected no exchanges to be returned",
                            key, misbehaviorEx != null ? misbehaviorEx.getExchangeId() : "<null>");
                    throw new OptimisticLockingException();
                }
            } else {
                DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(newExchange, true,
                        allowSerializedHeaders);
                CompletableFuture<DeleteResponse> completableDeleteResponse = kvClient
                        .delete(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()));
                DeleteResponse deleteResponse = completableDeleteResponse.get();
                if (deleteResponse.getDeleted() == 0) {
                    LOG.warn(
                            "Optimistic locking failed for exchange with key {}: kvClient.get returned no Exchanges, while it's expected to replace one",
                            key);
                    throw new OptimisticLockingException();
                }
                CompletableFuture<PutResponse> completablePutResponse = kvClient.put(
                        ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()), convertToEtcd3Format(newHolder));
                completablePutResponse.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error(e.getMessage(), e);
            throw new OptimisticLockingException();
        } catch (ExecutionException | IOException | ClassNotFoundException e) {
            LOG.error(e.getMessage(), e);
            throw new OptimisticLockingException();
        }
        LOG.trace("Added an Exchange with ID {} for key {} in optimistic manner.", newExchange.getExchangeId(), key);
        return oldExchange;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        if (optimistic) {
            throw new UnsupportedOperationException();
        }
        LOG.trace("Adding an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
        DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        CompletableFuture<GetResponse> completableResponse = kvClient
                .get(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()));
        try {
            GetResponse getResponse = completableResponse.get();
            long modRevision = 0;
            if (!getResponse.getKvs().isEmpty()) {
                modRevision = getResponse.getKvs().get(0).getModRevision();
            }
            Txn transaction = kvClient.txn();
            transaction
                    .If(new Cmp(
                            ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()),
                            Cmp.Op.EQUAL,
                            CmpTarget.modRevision(modRevision)))
                    .Then(Op.put(ByteSequence
                            .from(String.format("%s/%s", prefixName, key).getBytes()), convertToEtcd3Format(newHolder),
                            PutOption.DEFAULT))
                    .commit()
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error(e.getMessage(), e);
            throw new RuntimeCamelException(e.getMessage(), e);
        } catch (ExecutionException | IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeCamelException(e.getMessage(), e);
        }
        return unmarshallExchange(camelContext, newHolder);
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        if (useRecovery) {
            LOG.trace("Scanning for exchanges to recover in {} context", camelContext.getName());
            CompletableFuture<GetResponse> completableGetResponse = kvClient.get(
                    ByteSequence.from(persistencePrefixName.getBytes()),
                    GetOption.newBuilder().withPrefix(ByteSequence.from(persistencePrefixName.getBytes())).build());
            try {
                GetResponse getResponse = completableGetResponse.get();
                Set<String> keys = new TreeSet<>();
                getResponse.getKvs().forEach(kv -> keys.add(new String(kv.getKey().getBytes())));
                Set<String> scanned = Collections.unmodifiableSet(keys);
                LOG.trace("Found {} keys for exchanges to recover in {} context", scanned.size(),
                        camelContext.getName());
                return scanned;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error(e.getMessage(), e);
                throw new RuntimeCamelException(e.getMessage(), e);
            } catch (ExecutionException e) {
                LOG.error(e.getMessage(), e);
                throw new RuntimeCamelException(e.getMessage(), e);
            }
        } else {
            LOG.warn(
                    "What for to run recovery scans in {} context while prefix {} is running in non-recoverable aggregation repository mode?!",
                    camelContext.getName(), prefixName);
            return Collections.emptySet();
        }
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        LOG.trace("Recovering an Exchange with ID {}.", exchangeId);
        CompletableFuture<GetResponse> completableResponse = kvClient
                .get(ByteSequence.from(String.format("%s/%s", persistencePrefixName, exchangeId).getBytes()));
        try {
            GetResponse getResponse = completableResponse.get();
            DefaultExchangeHolder holder
                    = (DefaultExchangeHolder) convertFromEtcd3Format(getResponse.getKvs().get(0).getValue());
            return useRecovery ? unmarshallExchange(camelContext, holder) : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error(e.getMessage(), e);
            throw new RuntimeCamelException(e.getMessage(), e);
        } catch (ExecutionException | IOException | ClassNotFoundException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeCamelException(e.getMessage(), e);
        }
    }

    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    @Override
    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
    }

    @Override
    public long getRecoveryIntervalInMillis() {
        return recoveryInterval;
    }

    @Override
    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    @Override
    public boolean isUseRecovery() {
        return useRecovery;
    }

    @Override
    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterChannel = deadLetterUri;
    }

    @Override
    public String getDeadLetterUri() {
        return deadLetterChannel;
    }

    @Override
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    @Override
    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    public boolean isOptimistic() {
        return optimistic;
    }

    public void setOptimistic(boolean optimistic) {
        this.optimistic = optimistic;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getPrefixName() {
        return prefixName;
    }

    public void setPrefixName(String prefixName) {
        this.prefixName = prefixName;
    }

    @Override
    public Exchange get(CamelContext camelContext, String key) {
        CompletableFuture<GetResponse> completableResponse = kvClient
                .get(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()));
        try {
            GetResponse getResponse = completableResponse.get();
            DefaultExchangeHolder holder = null;
            if (!getResponse.getKvs().isEmpty()) {
                holder = (DefaultExchangeHolder) convertFromEtcd3Format(getResponse.getKvs().get(0).getValue());
            }
            return unmarshallExchange(camelContext, holder);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error(e.getMessage(), e);
            throw new RuntimeCamelException(e.getMessage(), e);
        } catch (ExecutionException | IOException | ClassNotFoundException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeCamelException(e.getMessage(), e);
        }
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        if (optimistic) {
            LOG.trace("Removing an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(),
                    key);
            try {
                CompletableFuture<GetResponse> completableGetResponse = kvClient
                        .get(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()));
                GetResponse getResponse = completableGetResponse.get();
                List<KeyValue> keyValueList = getResponse.getKvs();
                boolean optimisticLockingError = keyValueList.isEmpty();
                if (!optimisticLockingError) {
                    DefaultExchangeHolder holderFound = (DefaultExchangeHolder) convertFromEtcd3Format(
                            keyValueList.get(0).getValue());
                    optimisticLockingError = !Objects.equals(holder, holderFound);
                    if (!optimisticLockingError) {
                        CompletableFuture<DeleteResponse> completableDeleteResponse = kvClient
                                .delete(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()));
                        DeleteResponse deleteResponse = completableDeleteResponse.get();
                        optimisticLockingError = deleteResponse.getDeleted() == 0;
                    }
                }
                if (optimisticLockingError) {
                    LOG.warn(
                            "Optimistic locking failed for exchange with key {}: kvClient.delete removed no Exchanges, while it's expected to remove one.",
                            key);
                    throw new OptimisticLockingException();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error(e.getMessage(), e);
                throw new RuntimeCamelException(e.getMessage(), e);
            } catch (ExecutionException | ClassNotFoundException | IOException e) {
                LOG.error(e.getMessage(), e);
                throw new RuntimeCamelException(e.getMessage(), e);
            }
            LOG.trace("Removed an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(),
                    key);
            if (useRecovery) {
                LOG.trace(
                        "Putting an exchange with ID {} for key {} into a recoverable storage in an optimistic manner.",
                        exchange.getExchangeId(), key);
                try {
                    CompletableFuture<PutResponse> completablePutResponse = kvClient.put(
                            ByteSequence.from(String.format("%s/%s", persistencePrefixName, key).getBytes()),
                            convertToEtcd3Format(holder));
                    completablePutResponse.get();
                    LOG.trace(
                            "Put an exchange with ID {} for key {} into a recoverable storage in an optimistic manner.",
                            exchange.getExchangeId(), key);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error(e.getMessage(), e);
                    throw new RuntimeCamelException(e.getMessage(), e);
                } catch (IOException | ExecutionException e) {
                    LOG.error(e.getMessage(), e);
                    throw new RuntimeCamelException(e.getMessage(), e);
                }

            }
        } else {
            if (useRecovery) {
                LOG.trace("Removing an exchange with ID {} for key {} in a thread-safe manner.",
                        exchange.getExchangeId(), key);
                Txn transaction = kvClient.txn();
                try {
                    CompletableFuture<GetResponse> completableResponse = kvClient
                            .get(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()));
                    GetResponse getResponse = completableResponse.get();
                    DefaultExchangeHolder removedHolder
                            = (DefaultExchangeHolder) convertFromEtcd3Format(getResponse.getKvs().get(0).getValue());
                    transaction
                            .If(new Cmp(
                                    ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()),
                                    Cmp.Op.EQUAL,
                                    CmpTarget.value(
                                            ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()))))
                            .Then(Op.delete(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()),
                                    DeleteOption.DEFAULT),
                                    Op.put(ByteSequence
                                            .from(String.format("%s/%s", persistencePrefixName, key).getBytes()),
                                            convertToEtcd3Format(removedHolder), PutOption.DEFAULT))
                            .commit()
                            .get();
                    LOG.trace("Removed an exchange with ID {} for key {} in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                    LOG.trace(
                            "Put an exchange with ID {} for key {} into a recoverable storage in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeCamelException(e.getMessage(), e);
                } catch (Exception exception) {
                    throw new RuntimeCamelException(exception.getMessage(), exception);
                }
            } else {
                CompletableFuture<DeleteResponse> completableDeleteResponse = kvClient
                        .delete(ByteSequence.from(String.format("%s/%s", prefixName, key).getBytes()));
                try {
                    completableDeleteResponse.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error(e.getMessage(), e);
                    throw new RuntimeCamelException(e.getMessage(), e);
                } catch (ExecutionException e) {
                    LOG.error(e.getMessage(), e);
                    throw new RuntimeCamelException(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        LOG.trace("Confirming an exchange with ID {}.", exchangeId);
        if (useRecovery) {
            CompletableFuture<DeleteResponse> completableDeleteResponse = kvClient
                    .delete(ByteSequence.from(String.format("%s/%s", persistencePrefixName, exchangeId).getBytes()));
            try {
                completableDeleteResponse.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error(e.getMessage(), e);
                throw new RuntimeCamelException(e.getMessage(), e);
            } catch (ExecutionException e) {
                LOG.error(e.getMessage(), e);
                throw new RuntimeCamelException(e.getMessage(), e);
            }
        }
    }

    @Override
    public Set<String> getKeys() {
        CompletableFuture<GetResponse> completableGetResponse = kvClient.get(ByteSequence.from(prefixName.getBytes()),
                GetOption.newBuilder().withRange(ByteSequence.from(prefixName.getBytes())).build());
        Set<String> scanned;
        try {
            GetResponse getResponse = completableGetResponse.get();
            Set<String> keys = new TreeSet<>();
            getResponse.getKvs().forEach(kv -> keys.add(new String(kv.getKey().getBytes())));
            scanned = Collections.unmodifiableSet(keys);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error(e.getMessage(), e);
            throw new RuntimeCamelException(e.getMessage(), e);
        } catch (ExecutionException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeCamelException(e.getMessage(), e);
        }
        return scanned;
    }

    @Override
    protected void doInit() throws Exception {
        StringHelper.notEmpty(prefixName, "prefixName");
        if (maximumRedeliveries < 0) {
            throw new IllegalArgumentException("Maximum redelivery retries must be zero or a positive integer.");
        }
        if (recoveryInterval < 0) {
            throw new IllegalArgumentException("Recovery interval must be zero or a positive integer.");
        }

    }

    @Override
    protected void doStart() {
        if (client == null) {
            client = Client.builder().endpoints(endpoint).build();
            shutdownClient = true;
        }
        kvClient = client.getKVClient();
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null && shutdownClient) {
            client.close();
            client = null;
        }
    }

    protected Exchange unmarshallExchange(CamelContext camelContext, DefaultExchangeHolder holder) {
        Exchange exchange = null;
        if (holder != null) {
            exchange = new DefaultExchange(camelContext);
            DefaultExchangeHolder.unmarshal(exchange, holder);
        }
        return exchange;
    }

    private Object convertFromEtcd3Format(ByteSequence value) throws IOException, ClassNotFoundException {
        byte[] data = value.getBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is;
        try {
            is = new ObjectInputStream(in);
            return is.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOG.error(e.getMessage(), e);
            throw e;
        }
    }

    private ByteSequence convertToEtcd3Format(Object value) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(value);
            oos.flush();
            return ByteSequence.from(bos.toByteArray());
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw e;
        }
    }
}
