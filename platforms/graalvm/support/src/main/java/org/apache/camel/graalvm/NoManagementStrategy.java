package org.apache.camel.graalvm;

import java.util.Collections;
import java.util.EventObject;
import java.util.List;

import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementNamingStrategy;
import org.apache.camel.spi.ManagementObjectStrategy;
import org.apache.camel.spi.ManagementStrategy;

@SuppressWarnings("deprecated")
public class NoManagementStrategy implements ManagementStrategy {

    @Override
    public void manageObject(Object managedObject) throws Exception {

    }

    @Override
    public void manageNamedObject(Object managedObject, Object preferredName) throws Exception {

    }

    @Override
    public <T> T getManagedObjectName(Object managedObject, String customName, Class<T> nameType) throws Exception {
        return null;
    }

    @Override
    public void unmanageObject(Object managedObject) throws Exception {

    }

    @Override
    public void unmanageNamedObject(Object name) throws Exception {

    }

    @Override
    public boolean isManaged(Object managedObject, Object name) {
        return false;
    }

    @Override
    public void notify(EventObject event) throws Exception {

    }

    @Override
    public List<EventNotifier> getEventNotifiers() {
        return Collections.emptyList();
    }

    @Override
    public void setEventNotifiers(List<EventNotifier> eventNotifier) {

    }

    @Override
    public void addEventNotifier(EventNotifier eventNotifier) {

    }

    @Override
    public boolean removeEventNotifier(EventNotifier eventNotifier) {
        return false;
    }

    @Override
    public EventFactory getEventFactory() {
        return null;
    }

    @Override
    public void setEventFactory(EventFactory eventFactory) {

    }

    @Override
    public ManagementNamingStrategy getManagementNamingStrategy() {
        return null;
    }

    @Override
    public void setManagementNamingStrategy(ManagementNamingStrategy strategy) {

    }

    @Override
    public ManagementObjectStrategy getManagementObjectStrategy() {
        return null;
    }

    @Override
    public void setManagementObjectStrategy(ManagementObjectStrategy strategy) {

    }

    @Override
    public ManagementAgent getManagementAgent() {
        return null;
    }

    @Override
    public void setManagementAgent(ManagementAgent managementAgent) {

    }

    @Override
    public boolean manageProcessor(ProcessorDefinition<?> definition) {
        return false;
    }

    @Override
    public void onlyManageProcessorWithCustomId(boolean flag) {

    }

    @Override
    public boolean isOnlyManageProcessorWithCustomId() {
        return false;
    }

    @Override
    public void setLoadStatisticsEnabled(boolean flag) {

    }

    @Override
    public boolean isLoadStatisticsEnabled() {
        return false;
    }

    @Override
    public void setStatisticsLevel(ManagementStatisticsLevel level) {

    }

    @Override
    public ManagementStatisticsLevel getStatisticsLevel() {
        return null;
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {

    }
}
