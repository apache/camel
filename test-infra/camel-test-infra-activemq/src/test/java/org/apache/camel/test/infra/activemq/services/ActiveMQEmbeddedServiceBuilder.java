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

package org.apache.camel.test.infra.activemq.services;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import javax.management.ObjectName;

import org.apache.activemq.Service;
import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerContext;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.SslContext;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.broker.jmx.BrokerView;
import org.apache.activemq.broker.jmx.ManagementContext;
import org.apache.activemq.broker.region.DestinationFactory;
import org.apache.activemq.broker.region.DestinationInterceptor;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.broker.scheduler.JobSchedulerStore;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.network.jms.JmsConnector;
import org.apache.activemq.security.MessageAuthorizationPolicy;
import org.apache.activemq.store.PListStore;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.PersistenceAdapterFactory;
import org.apache.activemq.thread.TaskRunnerFactory;
import org.apache.activemq.usage.SystemUsage;
import org.apache.activemq.util.IOExceptionHandler;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is a builder class for the embedded ActiveMQ BrokerService. Since it is tightly integrated into the tests of
 * some components, we need to have flexibility setting it up. Therefore, in most cases for tests that rely on purely
 * embedded ActiveMQ, they can use this to wrap the broker service into the test-infra compatible service that can be
 * managed by Junit 5.
 */
public class ActiveMQEmbeddedServiceBuilder {
    private BrokerService brokerService;

    public ActiveMQEmbeddedServiceBuilder() {
        brokerService = new BrokerService();
    }

    public ActiveMQEmbeddedServiceBuilder withAdminView(BrokerView adminView) {
        brokerService.setAdminView(adminView);

        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withBrokerName(Class<?> testClass) {
        return withBrokerName(testClass.getSimpleName());
    }

    public ActiveMQEmbeddedServiceBuilder withBrokerName(String brokerName) {
        brokerService.setBrokerName(brokerName);

        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withBrokerName(Class<?> testClass, String name) {
        return withBrokerName(testClass.getSimpleName(), name);
    }

    public ActiveMQEmbeddedServiceBuilder withBrokerName(String brokerName, String name) {
        brokerService.setBrokerName(brokerName + (name != null ? "-" + name : ""));

        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDataDirectory(String dataDirectory) {
        brokerService.setDataDirectory(dataDirectory);

        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDataDirectoryFile(File dataDirectoryFile) {
        brokerService.setDataDirectoryFile(dataDirectoryFile);

        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withTmpDataDirectory(File tmpDataDirectory) {
        brokerService.setTmpDataDirectory(tmpDataDirectory);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPersistenceFactory(PersistenceAdapterFactory persistenceFactory) {
        brokerService.setPersistenceFactory(persistenceFactory);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDestinationFactory(DestinationFactory destinationFactory) {
        brokerService.setDestinationFactory(destinationFactory);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPersistent(boolean persistent) {
        brokerService.setPersistent(persistent);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPopulateJMSXUserID(boolean populateJMSXUserID) {
        brokerService.setPopulateJMSXUserID(populateJMSXUserID);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSystemUsage(SystemUsage memoryManager) {
        brokerService.setSystemUsage(memoryManager);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withConsumerSystemUsage(SystemUsage consumerSystemUsage) {
        brokerService.setConsumerSystemUsage(consumerSystemUsage);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withProducerSystemUsage(SystemUsage producerUsageManager) {
        brokerService.setProducerSystemUsage(producerUsageManager);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPersistenceAdapter(PersistenceAdapter persistenceAdapter) {

        try {
            brokerService.setPersistenceAdapter(persistenceAdapter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withTaskRunnerFactory(TaskRunnerFactory taskRunnerFactory) {
        brokerService.setTaskRunnerFactory(taskRunnerFactory);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPersistenceTaskRunnerFactory(TaskRunnerFactory persistenceTaskRunnerFactory) {
        brokerService.setPersistenceTaskRunnerFactory(persistenceTaskRunnerFactory);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withEnableStatistics(boolean enableStatistics) {
        brokerService.setEnableStatistics(enableStatistics);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseJmx(boolean useJmx) {
        brokerService.setUseJmx(useJmx);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withBrokerObjectName(ObjectName brokerObjectName) {
        brokerService.setBrokerObjectName(brokerObjectName);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withManagementContext(ManagementContext managementContext) {
        brokerService.setManagementContext(managementContext);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withNetworkConnectorURIs(String[] networkConnectorURIs) {
        brokerService.setNetworkConnectorURIs(networkConnectorURIs);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withTransportConnectorURIs(String[] transportConnectorURIs) {
        brokerService.setTransportConnectorURIs(transportConnectorURIs);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withJmsBridgeConnectors(JmsConnector[] jmsConnectors) {
        brokerService.setJmsBridgeConnectors(jmsConnectors);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withServices(Service[] services) {
        brokerService.setServices(services);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseLoggingForShutdownErrors(boolean useLoggingForShutdownErrors) {
        brokerService.setUseLoggingForShutdownErrors(useLoggingForShutdownErrors);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseShutdownHook(boolean useShutdownHook) {
        brokerService.setUseShutdownHook(useShutdownHook);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withAdvisorySupport(boolean advisorySupport) {
        brokerService.setAdvisorySupport(advisorySupport);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withAnonymousProducerAdvisorySupport(boolean anonymousProducerAdvisorySupport) {
        brokerService.setAnonymousProducerAdvisorySupport(anonymousProducerAdvisorySupport);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withTransportConnectors(List<TransportConnector> transportConnectors)
            throws Exception {
        brokerService.setTransportConnectors(transportConnectors);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withNetworkConnectors(List<?> networkConnectors) throws Exception {
        brokerService.setNetworkConnectors(networkConnectors);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withProxyConnectors(List<?> proxyConnectors) throws Exception {
        brokerService.setProxyConnectors(proxyConnectors);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDestinationPolicy(PolicyMap policyMap) {
        brokerService.setDestinationPolicy(policyMap);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPlugins(BrokerPlugin[] plugins) {
        brokerService.setPlugins(plugins);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withMessageAuthorizationPolicy(
            MessageAuthorizationPolicy messageAuthorizationPolicy) {
        brokerService.setMessageAuthorizationPolicy(messageAuthorizationPolicy);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDeleteAllMessagesOnStartup(boolean deletePersistentMessagesOnStartup) {
        brokerService.setDeleteAllMessagesOnStartup(deletePersistentMessagesOnStartup);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withVmConnectorURI(URI vmConnectorURI) {
        brokerService.setVmConnectorURI(vmConnectorURI);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withShutdownOnMasterFailure(boolean shutdownOnMasterFailure) {
        brokerService.setShutdownOnMasterFailure(shutdownOnMasterFailure);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withKeepDurableSubsActive(boolean keepDurableSubsActive) {
        brokerService.setKeepDurableSubsActive(keepDurableSubsActive);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withEnableMessageExpirationOnActiveDurableSubs(
            boolean enableMessageExpirationOnActiveDurableSubs) {
        brokerService.setEnableMessageExpirationOnActiveDurableSubs(enableMessageExpirationOnActiveDurableSubs);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseVirtualTopics(boolean useVirtualTopics) {
        brokerService.setUseVirtualTopics(useVirtualTopics);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseMirroredQueues(boolean useMirroredQueues) {
        brokerService.setUseMirroredQueues(useMirroredQueues);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDestinationInterceptors(DestinationInterceptor[] destinationInterceptors) {
        brokerService.setDestinationInterceptors(destinationInterceptors);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDestinations(ActiveMQDestination[] destinations) {
        brokerService.setDestinations(destinations);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withTempDataStore(PListStore tempDataStore) {
        brokerService.setTempDataStore(tempDataStore);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPersistenceThreadPriority(int persistenceThreadPriority) {
        brokerService.setPersistenceThreadPriority(persistenceThreadPriority);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseLocalHostBrokerName(boolean useLocalHostBrokerName) {
        brokerService.setUseLocalHostBrokerName(useLocalHostBrokerName);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withProducerSystemUsagePortion(int producerSystemUsagePortion) {
        brokerService.setProducerSystemUsagePortion(producerSystemUsagePortion);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withConsumerSystemUsagePortion(int consumerSystemUsagePortion) {
        brokerService.setConsumerSystemUsagePortion(consumerSystemUsagePortion);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSplitSystemUsageForProducersConsumers(
            boolean splitSystemUsageForProducersConsumers) {
        brokerService.setSplitSystemUsageForProducersConsumers(splitSystemUsageForProducersConsumers);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withMonitorConnectionSplits(boolean monitorConnectionSplits) {
        brokerService.setMonitorConnectionSplits(monitorConnectionSplits);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withTaskRunnerPriority(int taskRunnerPriority) {
        brokerService.setTaskRunnerPriority(taskRunnerPriority);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDedicatedTaskRunner(boolean dedicatedTaskRunner) {
        brokerService.setDedicatedTaskRunner(dedicatedTaskRunner);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withCacheTempDestinations(boolean cacheTempDestinations) {
        brokerService.setCacheTempDestinations(cacheTempDestinations);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withTimeBeforePurgeTempDestinations(int timeBeforePurgeTempDestinations) {
        brokerService.setTimeBeforePurgeTempDestinations(timeBeforePurgeTempDestinations);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseTempMirroredQueues(boolean useTempMirroredQueues) {
        brokerService.setUseTempMirroredQueues(useTempMirroredQueues);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withJobSchedulerStore(JobSchedulerStore jobSchedulerStore) {
        brokerService.setJobSchedulerStore(jobSchedulerStore);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withShutdownHooks(List<Runnable> hooks) throws Exception {
        brokerService.setShutdownHooks(hooks);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withRegionBroker(Broker regionBroker) {
        brokerService.setRegionBroker(regionBroker);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSystemExitOnShutdown(boolean systemExitOnShutdown) {
        brokerService.setSystemExitOnShutdown(systemExitOnShutdown);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSystemExitOnShutdownExitCode(int systemExitOnShutdownExitCode) {
        brokerService.setSystemExitOnShutdownExitCode(systemExitOnShutdownExitCode);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSslContext(SslContext sslContext) {
        brokerService.setSslContext(sslContext);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withShutdownOnSlaveFailure(boolean shutdownOnSlaveFailure) {
        brokerService.setShutdownOnSlaveFailure(shutdownOnSlaveFailure);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withWaitForSlave(boolean waitForSlave) {
        brokerService.setWaitForSlave(waitForSlave);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withWaitForSlaveTimeout(long waitForSlaveTimeout) {
        brokerService.setWaitForSlaveTimeout(waitForSlaveTimeout);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPassiveSlave(boolean passiveSlave) {
        brokerService.setPassiveSlave(passiveSlave);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withIoExceptionHandler(IOExceptionHandler ioExceptionHandler) {
        brokerService.setIoExceptionHandler(ioExceptionHandler);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSchedulerSupport(boolean schedulerSupport) {
        brokerService.setSchedulerSupport(schedulerSupport);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSchedulerDirectoryFile(File schedulerDirectory) {
        brokerService.setSchedulerDirectoryFile(schedulerDirectory);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSchedulerDirectory(String schedulerDirectory) {
        brokerService.setSchedulerDirectory(schedulerDirectory);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSchedulePeriodForDestinationPurge(int schedulePeriodForDestinationPurge) {
        brokerService.setSchedulePeriodForDestinationPurge(schedulePeriodForDestinationPurge);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withSchedulePeriodForDiskUsageCheck(int schedulePeriodForDiskUsageCheck) {
        brokerService.setSchedulePeriodForDiskUsageCheck(schedulePeriodForDiskUsageCheck);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withDiskUsageCheckRegrowThreshold(int diskUsageCheckRegrowThreshold) {
        brokerService.setDiskUsageCheckRegrowThreshold(diskUsageCheckRegrowThreshold);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withMaxPurgedDestinationsPerSweep(int maxPurgedDestinationsPerSweep) {
        brokerService.setMaxPurgedDestinationsPerSweep(maxPurgedDestinationsPerSweep);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withBrokerContext(BrokerContext brokerContext) {
        brokerService.setBrokerContext(brokerContext);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withBrokerId(String brokerId) {
        brokerService.setBrokerId(brokerId);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseAuthenticatedPrincipalForJMSXUserID(
            boolean useAuthenticatedPrincipalForJMSXUserID) {
        brokerService.setUseAuthenticatedPrincipalForJMSXUserID(useAuthenticatedPrincipalForJMSXUserID);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withPopulateUserNameInMBeans(boolean value) {
        brokerService.setPopulateUserNameInMBeans(value);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withMbeanInvocationTimeout(long mbeanInvocationTimeout) {
        brokerService.setMbeanInvocationTimeout(mbeanInvocationTimeout);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withNetworkConnectorStartAsync(boolean networkConnectorStartAsync) {
        brokerService.setNetworkConnectorStartAsync(networkConnectorStartAsync);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withAllowTempAutoCreationOnSend(boolean allowTempAutoCreationOnSend) {
        brokerService.setAllowTempAutoCreationOnSend(allowTempAutoCreationOnSend);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withOfflineDurableSubscriberTimeout(long offlineDurableSubscriberTimeout) {
        brokerService.setOfflineDurableSubscriberTimeout(offlineDurableSubscriberTimeout);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withOfflineDurableSubscriberTaskSchedule(
            long offlineDurableSubscriberTaskSchedule) {
        brokerService.setOfflineDurableSubscriberTaskSchedule(offlineDurableSubscriberTaskSchedule);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withStartAsync(boolean startAsync) {
        brokerService.setStartAsync(startAsync);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withRestartAllowed(boolean restartAllowed) {
        brokerService.setRestartAllowed(restartAllowed);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withStoreOpenWireVersion(int storeOpenWireVersion) {
        brokerService.setStoreOpenWireVersion(storeOpenWireVersion);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withRejectDurableConsumers(boolean rejectDurableConsumers) {
        brokerService.setRejectDurableConsumers(rejectDurableConsumers);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseVirtualDestSubs(boolean useVirtualDestSubs) {
        brokerService.setUseVirtualDestSubs(useVirtualDestSubs);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withUseVirtualDestSubsOnCreation(boolean useVirtualDestSubsOnCreation) {
        brokerService.setUseVirtualDestSubsOnCreation(useVirtualDestSubsOnCreation);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withAdjustUsageLimits(boolean adjustUsageLimits) {
        brokerService.setAdjustUsageLimits(adjustUsageLimits);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withRollbackOnlyOnAsyncException(boolean rollbackOnlyOnAsyncException) {
        brokerService.setRollbackOnlyOnAsyncException(rollbackOnlyOnAsyncException);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withMaxSchedulerRepeatAllowed(int maxSchedulerRepeatAllowed) {
        brokerService.setMaxSchedulerRepeatAllowed(maxSchedulerRepeatAllowed);
        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withCustomSetup(Consumer<BrokerService> consumer) {
        consumer.accept(brokerService);

        return this;
    }

    public ActiveMQEmbeddedServiceBuilder withTcpTransport() {
        return withTcpTransport(0);
    }

    public ActiveMQEmbeddedServiceBuilder withTcpTransport(int port) {
        return withTransport("tcp://0.0.0.0:", port);
    }

    public ActiveMQEmbeddedServiceBuilder withAmqpTransport(int port) {
        return withTransport("amqp://0.0.0.0:", port);
    }

    public ActiveMQEmbeddedServiceBuilder withMqttTransport(int port) {
        return withTransport("mqtt://0.0.0.0:", port);
    }

    public ActiveMQEmbeddedServiceBuilder withStompTransport(int port, String options) {
        return withTransport("stomp://localhost:", port, options);
    }

    public ActiveMQEmbeddedServiceBuilder withTransport(String bindAddress, int port) {
        return withTransport(bindAddress, port, null);
    }

    public ActiveMQEmbeddedServiceBuilder withTransport(String bindAddress, int port, String options) {
        try {
            brokerService.addConnector(bindAddress + port + (options == null ? "" : options));
        } catch (Exception e) {
            fail("Unable to add new transport: " + e.getMessage());
        }

        return this;
    }

    BrokerService brokerService() {
        return brokerService;
    }

    public ActiveMQEmbeddedService build() {
        return new ActiveMQEmbeddedService(brokerService);
    }

    public ActiveMQEmbeddedService buildWithRecycle() {
        return new ActiveMQEmbeddedService(brokerService, true);
    }

    public static ActiveMQEmbeddedServiceBuilder bare() {
        return new ActiveMQEmbeddedServiceBuilder();
    }

    private static String generateDataDirectoryPathForInstance(String name) {
        return ActiveMQEmbeddedServiceBuilder.class.getResource("/").getFile() + name
               + ThreadLocalRandom.current().nextInt(1000);
    }

    public static ActiveMQEmbeddedServiceBuilder defaultBroker() {
        return defaultBroker(ActiveMQEmbeddedServiceBuilder.class.getSimpleName());
    }

    public static ActiveMQEmbeddedServiceBuilder defaultBroker(String name) {
        final String dataDirectoryPath = generateDataDirectoryPathForInstance(name);
        return new ActiveMQEmbeddedServiceBuilder()
                .withDeleteAllMessagesOnStartup(true)
                .withBrokerName(name)
                .withAdvisorySupport(false)
                .withUseJmx(false)
                .withDataDirectory(dataDirectoryPath);
    }

    public static ActiveMQEmbeddedServiceBuilder persistentBroker() {
        final String name = "persistent" + ActiveMQEmbeddedServiceBuilder.class.getSimpleName() + ThreadLocalRandom.current().nextInt(1000);
        return persistentBroker(name);
    }

    public static ActiveMQEmbeddedServiceBuilder persistentBroker(String name) {
        final String dataDirectoryPath = generateDataDirectoryPathForInstance(name);

        return new ActiveMQEmbeddedServiceBuilder()
                .withDeleteAllMessagesOnStartup(true)
                .withBrokerName(name)
                .withAdvisorySupport(false)
                .withUseJmx(false)
                .withPersistent(true)
                .withDataDirectory(dataDirectoryPath);
    }

}
