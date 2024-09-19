package org.apache.camel.component.clickup;

import org.apache.camel.Endpoint;
import org.apache.camel.component.clickup.model.Events;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

import java.util.Map;
import java.util.Set;

@Component("clickup")
public class ClickUpComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ClickUpConfiguration configuration = new ClickUpConfiguration();

        if (remaining.endsWith("/")) {
            remaining = remaining.substring(0, remaining.length() - 1);
        }
        Long workspaceId = Long.parseLong(remaining);
        configuration.setWorkspaceId(workspaceId);

        ClickUpEndpoint endpoint = new ClickUpEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAuthorizationToken())) {
            throw new IllegalArgumentException("AuthorizationToken must be configured for clickup: " + uri);
        }

        if (!Events.areAllEventsSupported(endpoint.getConfiguration().getEvents())) {
            Set<String> unsupportedEvents = Events.computeUnsupportedEvents(endpoint.getConfiguration().getEvents());

            throw new IllegalArgumentException("The following events are not yet supported: " + unsupportedEvents);
        }

        return endpoint;
    }

}
