package org.apache.camel.component.azure.servicebus.integration;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.ServiceBusConfiguration;
import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.ServiceBusService;
import com.microsoft.windowsazure.services.servicebus.models.*;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.Utilities;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

@Ignore("Integration test! Must be run manually.")
public class MoveMessagesIntegrationTest {
    private String namespace = "alanliu";
    private String serviceBusRootUri = ".servicebus.windows.net";
    private String sasKeyName = "RootManageSharedAccessKey";
    private String sasKey = "lbtrbyCl5CfLQURx9FqdoHxHy+tNRdk1lLIjk8Hh+Ms=";
    private String fromQueueName = "myque";
    private String toQueueName = "myque2";
    final String fromQueueEndpointUri = "azure-sb://RootManageSharedAccessKey:lbtrbyCl5CfLQURx9FqdoHxHy%2BtNRdk1lLIjk8Hh%2BMs%3D@alanliu.servicebus.windows.net/queue?queueName=from_queue";
    final String toTopicEndpointUri = "azure-sb://RootManageSharedAccessKey:lbtrbyCl5CfLQURx9FqdoHxHy%2BtNRdk1lLIjk8Hh%2BMs%3D@alanliu.servicebus.windows.net/topic?topicPath=mytopic";
    final String fromSubscriptionEndpointUri = "azure-sb://RootManageSharedAccessKey:lbtrbyCl5CfLQURx9FqdoHxHy%2BtNRdk1lLIjk8Hh%2BMs%3D@alanliu.servicebus.windows.net/topic?topicPath=mytopic&subscriptionName=mysubcription";
    final String toQueueEndpointUri = "azure-sb://RootManageSharedAccessKey:lbtrbyCl5CfLQURx9FqdoHxHy%2BtNRdk1lLIjk8Hh%2BMs%3D@alanliu.servicebus.windows.net/queue?queueName=to_queue";
    final String jndiFromQueueUri = "azure-sb://queue?queueName=from_queue&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true";
    final String jndiToQueueUri = "azure-sb://queue?queueName=to_queue&ServiceBusContract=#MyServiceBusContract";
    final String jndiFromTopicUri = "azure-sb://topic?topicPath=mytopic&subscriptionName=mysubcription&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true";
    final String jndiToTopicUri = "azure-sb://topic?topicPath=mytopic&ServiceBusContract=#MyServiceBusContract";
    private ServiceBusContract service;
    private CamelContext context;

    @Before
    public void setup() throws Exception {
        createService();
        createContext();
    }

    @Test
    public void sendInOnly() throws Exception {
        List<String> sentMessages = new ArrayList<>();
        //put some messages in from_queue
        for (int i = 0; i < 5; i++) {
            String msg = "Hello World, I'm " + i;
            sendMessageToQue("from_queue", msg);
            sentMessages.add(msg);
        }
        // move messages from from_queue to to_queue by camel.
        // add our route to the CamelContext
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from(jndiFromQueueUri).to(jndiToTopicUri);
                from(jndiFromTopicUri).to(jndiToQueueUri);
//                from(fromQueueEndpointUri).to(toQueueEndpointUri);
//                from(fromQueueEndpointUri).to(toTopicEndpointUri);
//                from(fromSubscriptionEndpointUri).to(toQueueEndpointUri);
            }
        });

        // start the route and let it do its work
        context.start();
        Thread.sleep(1000 * 100); //Waiting everything done.

        // stop the CamelContext
        context.stop();

        List<String> recievedMessages = new ArrayList<>();

        // pick messages from to_queue to check
        for (int i = 0; i < 5; i++) {
            String msg = peekLock("to_queue");
            recievedMessages.add(msg);
        }
        //check messages
        for (int i=0; i<5; i++){
            System.out.println("recievedMessages[" + i + "]:" + recievedMessages.get(i));
            assertTrue(recievedMessages.contains(sentMessages.get(i)));
        }

    }

    private void sendMessageToQue(String que, String msg) {
        try {
            BrokeredMessage message = new BrokeredMessage(msg);
            service.sendQueueMessage(que, message);
        } catch (ServiceException e) {
            System.out.print("ServiceException encountered: ");
            System.out.println(e.getMessage());
            System.exit(-1);
        }
    }

    private String peekLock(String que) throws ServiceException, IOException {

        ReceiveMessageOptions opts = ReceiveMessageOptions.DEFAULT;
        opts.setReceiveMode(ReceiveMode.PEEK_LOCK);

        ReceiveQueueMessageResult resultQM =
                service.receiveQueueMessage(que, opts);
        BrokeredMessage message = resultQM.getValue();
        if (message != null && message.getMessageId() != null) {
            String result = Utilities.readString(message.getBody());
            service.deleteMessage(message);
            return result;
        } else {
            System.out.println("Finishing up - no more messages.");
            return null;
        }

    }

    private void createService() {
        Configuration config = ServiceBusConfiguration.configureWithSASAuthentication("alanliu",
                "RootManageSharedAccessKey",
                "lbtrbyCl5CfLQURx9FqdoHxHy+tNRdk1lLIjk8Hh+Ms=",
                ".servicebus.windows.net");
        service = ServiceBusService.create(config);
    }

    private void createContext() throws Exception {

        JndiRegistry registry = new JndiRegistry(createJndiContext());
        createService();

        GetQueueResult queueResult = service.getQueue(fromQueueName);

        registry.bind("MyServiceBusContract", service);
        context = new DefaultCamelContext(registry);
        ServiceBusContract serviceBusContract = (ServiceBusContract) context.getRegistry().lookupByName("MyServiceBusContract");

        GetQueueResult queueResult2 = serviceBusContract.getQueue(fromQueueName);

    }

    protected Context createJndiContext() throws Exception {
        Properties properties = new Properties();
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("jndi.properties");
        if (in != null) {
            properties.load(in);
        } else {
            properties.put("java.naming.factory.initial", "org.apache.camel.util.jndi.CamelInitialContextFactory");
        }

        return new InitialContext(new Hashtable(properties));
    }
}
