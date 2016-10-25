package org.apache.camel.component.windowsazure.servicebus;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

import java.util.Map;

/**
 * Created by alan on 14/10/16.
 */
public class SbComponent extends UriEndpointComponent {
    private EntityType toEntityType(String s) {
        switch (s) {
            case "queue":
                return EntityType.QUEUE;
            case "topic":
                return EntityType.TOPIC;
            case "event":
                return EntityType.EVENT;
            default:
                throw new IllegalArgumentException("Entities type should be: queue/topic/event.");
        }
    }
    public SbComponent() {
        super(AbstractSbEndpoint.class);
    }

    public SbComponent(CamelContext context) {
        super(context, AbstractSbEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SbConfiguration configuration = new SbConfiguration();
        setProperties(configuration, parameters);
//"azure-sb://queue?queueName=MyQueue&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true
// azure-sb://<sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>/<queue>?queueName=<queueName>&timeout=<timeout>&peekLock=<peekLock>
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Entities must be specified.");
        }

        if (remaining.contains("/")) {
            String[] parts = remaining.split("/");
            if (parts.length !=2) {
                throw new IllegalArgumentException("1.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>/<entities>.");
            }
            configuration.setEntities(toEntityType(parts[1]));

            if (remaining.contains("@")) {
                String[] siteParts = parts[0].split("@");
                if (siteParts.length != 2) {
                    throw new IllegalArgumentException("2.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>.");
                }
                String[] sasParts = siteParts[0].split(":");
                if (sasParts.length != 2) {
                    throw new IllegalArgumentException("3.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>.");
                }

                configuration.setSasKeyName(sasParts[0]);
                configuration.setSasKey(sasParts[1]);
                String[] domainParts = siteParts[1].split("\\.");
                if (domainParts.length < 2) {
                    throw new IllegalArgumentException("4.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>.");
                }
                configuration.setNamespace(domainParts[0]);
                configuration.setServiceBusRootUri(siteParts[1].substring(domainParts[0].length()));

            } else {
                throw new IllegalArgumentException("5.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>.");
            }
        }else {
            configuration.setEntities(toEntityType(remaining));
        }

        if (configuration.getServiceBusContract() == null && (
                configuration.getSasKey() == null
                || configuration.getSasKeyName() == null
                || configuration.getServiceBusRootUri() == null
                || configuration.getNamespace() == null)) {
            throw new IllegalArgumentException("serviceBusContract or sasKey, sasKeyName, serviceBusRootUri and namespace must be specified.");
        }

        AbstractSbEndpoint abstractSbEndpoint;
        switch (configuration.getEntities()){
            case QUEUE:
                abstractSbEndpoint = new SbQueueEndpoint(uri, this, configuration);
                break;
            case TOPIC:
                abstractSbEndpoint = new SbTopicEndpoint(uri, this, configuration);
                break;
            case EVENT:
                abstractSbEndpoint = new SbEventEndpoint(uri, this, configuration);
                break;
            default:
                throw new Exception("Bad entities chanel.");
        }
        abstractSbEndpoint.setConsumerProperties(parameters);
        return abstractSbEndpoint;
    }
}
