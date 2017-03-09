package org.apache.camel.component.azure.servicebus;

import static org.apache.camel.component.azure.servicebus.SbConstants.EntityType.QUEUE;
import static org.apache.camel.component.azure.servicebus.SbConstants.EntityType.TOPIC;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import com.microsoft.windowsazure.services.servicebus.models.ReceiveMode;

public class SbComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createTopicEndpointWithServiceBusContractAndMaximalConfiguration() throws Exception {
        ServiceBusContractMock mock = new ServiceBusContractMock();
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("MyServiceBusContract", mock);

        SbComponent component = new SbComponent(context);
        AbstractSbEndpoint endpoint = (AbstractSbEndpoint) component.createEndpoint(
                "azure-sb://topic?topicPath=mytopic&subscriptionName=mysubcription&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true"
        );

        assertEquals("mytopic", endpoint.getConfiguration().getTopicPath());
        assertEquals("mysubcription", endpoint.getConfiguration().getSubscriptionName());
        assertEquals(TOPIC,endpoint.getConfiguration().getEntities());
        assertSame(mock, endpoint.getConfiguration().getServiceBusContract());
        assertEquals(new Integer(2000), endpoint.getConfiguration().getTimeout());
        assertEquals(ReceiveMode.PEEK_LOCK, endpoint.getConfiguration().getReceiveMode());
    }

    @Test
    public void createTopicEndpointWithServiceBusContractAndMinimalConfiguration() throws Exception {
        ServiceBusContractMock mock = new ServiceBusContractMock();
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("MyServiceBusContract", mock);

        SbComponent component = new SbComponent(context);
        AbstractSbEndpoint endpoint = (AbstractSbEndpoint) component.createEndpoint("azure-sb://topic?topicPath=mytopic&subscriptionName=mysubcription&ServiceBusContract=#MyServiceBusContract");

        assertEquals("mytopic", endpoint.getConfiguration().getTopicPath());
        assertEquals("mysubcription", endpoint.getConfiguration().getSubscriptionName());
        assertEquals(TOPIC, endpoint.getConfiguration().getEntities());
        assertSame(mock, endpoint.getConfiguration().getServiceBusContract());
        assertNull(endpoint.getConfiguration().getTimeout());
        assertEquals(ReceiveMode.RECEIVE_AND_DELETE, endpoint.getConfiguration().getReceiveMode());
    }
    @Test
    public void createQueueEndpointWithServiceBusContractAndMaximalConfiguration() throws Exception {
        ServiceBusContractMock mock = new ServiceBusContractMock();
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("MyServiceBusContract", mock);

        SbComponent component = new SbComponent(context);
        AbstractSbEndpoint endpoint = (AbstractSbEndpoint) component.createEndpoint(
                "azure-sb://queue?queueName=MyQueue&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true"
        );

        assertEquals("MyQueue", endpoint.getConfiguration().getQueueName());
        assertEquals(QUEUE,endpoint.getConfiguration().getEntities());
        assertSame(mock, endpoint.getConfiguration().getServiceBusContract());
        assertEquals(new Integer(2000), endpoint.getConfiguration().getTimeout());
        assertEquals(ReceiveMode.PEEK_LOCK, endpoint.getConfiguration().getReceiveMode());
    }

    @Test
    public void createQueueEndpointWithServiceBusContractAndMinimalConfiguration() throws Exception {
        ServiceBusContractMock mock = new ServiceBusContractMock();
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("MyServiceBusContract", mock);

        SbComponent component = new SbComponent(context);
        AbstractSbEndpoint endpoint = (AbstractSbEndpoint) component.createEndpoint("azure-sb://queue?queueName=MyQueue&ServiceBusContract=#MyServiceBusContract");

        assertEquals("MyQueue", endpoint.getConfiguration().getQueueName());
        assertEquals(QUEUE,endpoint.getConfiguration().getEntities());
        assertSame(mock, endpoint.getConfiguration().getServiceBusContract());
        assertNull(endpoint.getConfiguration().getTimeout());
        assertEquals(ReceiveMode.RECEIVE_AND_DELETE, endpoint.getConfiguration().getReceiveMode());
    }
    @Test
    public void createQueueEndpointWithMinimalConfiguration() throws Exception {
        SbComponent component = new SbComponent(context);
        AbstractSbEndpoint endpoint = (AbstractSbEndpoint) component.createEndpoint("azure-sb://RootManageSharedAccessKey:lbtrbyCl5CfLQURx9FqdoHxHy%2BtNRdk1lLIjk8Hh%2BMs%3D@alanliu.servicebus.windows.net/queue?queueName=MyQueue");

        assertEquals("MyQueue", endpoint.getConfiguration().getQueueName());
        assertEquals(QUEUE,endpoint.getConfiguration().getEntities());
        assertEquals("RootManageSharedAccessKey", endpoint.getConfiguration().getSasKeyName());
        assertEquals("lbtrbyCl5CfLQURx9FqdoHxHy+tNRdk1lLIjk8Hh+Ms=", endpoint.getConfiguration().getSasKey());
        assertEquals("alanliu", endpoint.getConfiguration().getNamespace());
        assertEquals(".servicebus.windows.net", endpoint.getConfiguration().getServiceBusRootUri());
        assertNull(endpoint.getConfiguration().getServiceBusContract());
        assertNull(endpoint.getConfiguration().getTimeout());
        assertEquals(ReceiveMode.RECEIVE_AND_DELETE, endpoint.getConfiguration().getReceiveMode());
    }
    @Test
    public void createQueueEndpointWithMaximallConfiguration() throws Exception {
        SbComponent component = new SbComponent(context);
        AbstractSbEndpoint endpoint = (AbstractSbEndpoint) component.createEndpoint("azure-sb://RootManageSharedAccessKey:lbtrbyCl5CfLQURx9FqdoHxHy%2BtNRdk1lLIjk8Hh%2BMs%3D@alanliu.servicebus.windows.net/queue?queueName=MyQueue&timeout=2000&peekLock=true");

        assertEquals("MyQueue", endpoint.getConfiguration().getQueueName());
        assertEquals(QUEUE,endpoint.getConfiguration().getEntities());
        assertEquals("RootManageSharedAccessKey", endpoint.getConfiguration().getSasKeyName());
        assertEquals("lbtrbyCl5CfLQURx9FqdoHxHy+tNRdk1lLIjk8Hh+Ms=", endpoint.getConfiguration().getSasKey());
        assertEquals("alanliu", endpoint.getConfiguration().getNamespace());
        assertEquals(".servicebus.windows.net", endpoint.getConfiguration().getServiceBusRootUri());
        assertNull(endpoint.getConfiguration().getServiceBusContract());
        assertEquals(new Integer(2000), endpoint.getConfiguration().getTimeout());
        assertEquals(ReceiveMode.PEEK_LOCK, endpoint.getConfiguration().getReceiveMode());
    }
}
