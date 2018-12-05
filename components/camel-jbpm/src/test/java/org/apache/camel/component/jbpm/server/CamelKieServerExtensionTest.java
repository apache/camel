package org.apache.camel.component.jbpm.server;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.apache.camel.model.rest.RestDefinition;
import org.jbpm.services.api.service.ServiceRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.runtime.KieContainer;
import org.kie.server.services.api.KieContainerInstance;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


@RunWith(MockitoJUnitRunner.class)
public class CamelKieServerExtensionTest {

    
    @Mock
    private KieContainerInstance kieContainerInstance;
    
    @Mock
    private KieContainer kieContainer;
    
    @Test
    public void testInit() {
        CamelKieServerExtension extension = new CamelKieServerExtension();
        extension.init(null, null);
        CamelContext globalCamelContext = (CamelContext) ServiceRegistry.get().service("GlobalCamelService");
        List<RestDefinition> globalRestDefinitions = globalCamelContext.getRestDefinitions();
        assertThat(globalRestDefinitions.size(), equalTo(1));
        assertThat(globalCamelContext.getRouteDefinition("unitTestRoute"), is(notNullValue()));
    }
    
    @Test
    public void testCreateContainer() {
        CamelKieServerExtension extension = new CamelKieServerExtension();
        final String containerId = "testContainer";
        
        when(kieContainerInstance.getKieContainer()).thenReturn(kieContainer);
        when(kieContainer.getClassLoader()).thenReturn(this.getClass().getClassLoader());
        
        extension.createContainer(containerId, kieContainerInstance, new HashMap<String, Object>());
        
        CamelContext camelContext = (CamelContext) ServiceRegistry.get().service("testContainer" + JBPMConstants.DEPLOYMENT_CAMEL_CONTEXT_SERVICE_KEY_POSTFIX);
        List<RestDefinition> restDefinitions = camelContext.getRestDefinitions();
        assertThat(restDefinitions.size(), equalTo(1));
        
        assertThat(camelContext.getRoute("unitTestRoute"), is(notNullValue()));
    }
    
    
}
