package org.apache.camel.management;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementNamingStrategyAware;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RouteContext;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(EasyMockRunner.class)
public class ManagementNamingStrategyAwareTest {
    
    @Mock
    private Component component;
    
    @Mock
    private ManagementNamingStrategyAwareComponent awareComponent;
    
    @Mock
    private Endpoint endpoint;
    
    @Mock
    private ManagementNamingStrategyAwareEndpoint awareEndpoint;
    
    @Mock
    private DataFormat dataFormat;
    
    @Mock
    private ManagementNamingStrategyAwareDataFormat awareDataFormat;

    @Mock
    private ErrorHandlerFactory errorHandlerFactory;
    
    @Mock
    private ManagementNamingStrategyAwareErrorHandlerFactory awareErrorHandlerFactory;
    
    @Mock
    private RouteContext routeContext;
    
    @Mock
    private Processor processor;
    
    @Mock
    private ManagementNamingStrategyAwareProcessor awareProcessor;

    @Mock
    private Route route;
    
    @Mock
    private ManagementNamingStrategyAwareRoute awareRoute;

    @Mock
    private Consumer consumer;
    
    @Mock
    private ManagementNamingStrategyAwareConsumer awareConsumer;
    
    @Mock
    private Producer producer;
    
    @Mock
    private ManagementNamingStrategyAwareProducer awareProducer;
    
    @Mock
    private InterceptStrategy tracer;
    
    @Mock
    private ManagementNamingStrategyAwareTracer awareTracer;

    @Mock
    private Service service;
    
    @Mock
    private ManagementNamingStrategyAwareService awareService;
    
    @Mock
    private EventNotifier eventNotifier;
    
    @Mock
    private ManagementNamingStrategyAwareEventNotifier awareEventNotifier;
    
    @Mock
    private NamedNode definition;

    private CamelContext camelContext;
    
    private ManagementStrategy managementStrategy;
    
    private ManagementAgent managementAgent;
    
    @TestSubject
    private DefaultManagementNamingStrategy defaultManagementNamingStrategy = new DefaultManagementNamingStrategy();
    
    @Before
    public void beforeTest() {
        // @Mock does not work BEFORE @Before method
        
        managementAgent = createMock(ManagementAgent.class);
        expect(managementAgent.getIncludeHostName()).andStubReturn(false);
        expect(managementAgent.getMask()).andStubReturn(false);
        replay(managementAgent);
        
        managementStrategy = createMock(ManagementStrategy.class);
        expect(managementStrategy.getManagementAgent()).andStubReturn(managementAgent);
        replay(managementStrategy);
        
        camelContext = createMock(CamelContext.class);
        expect(camelContext.getManagementStrategy()).andStubReturn(managementStrategy);
        expect(camelContext.getName()).andStubReturn("camelContext");
        expect(camelContext.getManagementName()).andStubReturn(null);

        replay(camelContext);
        
        defaultManagementNamingStrategy.setCamelContext(camelContext);
    }
    
    @Test
    public void testObjectNameForComponent() throws Exception {
        expect(component.getCamelContext()).andStubReturn(camelContext);
        replay(component);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForComponent(component, "testComponent");
        
        assertEquals("org.apache.camel:context=camelContext,type=components,name=\"testComponent\"",
                name.toString());
    }
    
    @Test
    public void testObjectnameForAwareComponent() throws Exception {
        expect(awareComponent.getCamelContext()).andStubReturn(camelContext);
        expect(awareComponent.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareComponent);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForComponent(awareComponent, "testComponent");
        
        assertEquals("org.apache.camel:context=camelContext,type=components,name=\"testComponent\",testKey=\"testValue\"",
                name.toString());
    }

    @Test
    public void testObjectNameForEndpoint() throws Exception {
        expect(endpoint.getCamelContext()).andStubReturn(camelContext);
        expect(endpoint.isSingleton()).andStubReturn(false);
        expect(endpoint.getEndpointKey()).andStubReturn("someKey");
        replay(endpoint);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForEndpoint(endpoint);
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=endpoints,name=\"someKey\\?id="));
    }
    
    @Test
    public void testObjectNameForAwareEndpoint() throws Exception {
        expect(awareEndpoint.getCamelContext()).andStubReturn(camelContext);
        expect(awareEndpoint.isSingleton()).andStubReturn(false);
        expect(awareEndpoint.getEndpointKey()).andStubReturn("someKey");
        expect(awareEndpoint.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareEndpoint);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForEndpoint(awareEndpoint);
        assertEquals("org.apache.camel:context=camelContext,type=endpoints,name=\"someKey\",testKey=\"testValue\"",
                name.toString());
    }

    
    @Test
    public void testObjectNameForDataFormat() throws Exception {
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForDataFormat(camelContext, dataFormat);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=dataformats,name="));
        assertTrue(name.toString(), name.toString().endsWith(")"));
    }
    
    @Test
    public void testObjectNameForAwareDataFormat() throws Exception {
        expect(awareDataFormat.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareDataFormat);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForDataFormat(camelContext, awareDataFormat);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=dataformats,name="));
        assertTrue(name.toString(), name.toString().endsWith(",testKey=\"testValue\""));
    }
    
    @Test
    public void testObjectNameForErrorHandlerFactory() throws Exception {
        expect(routeContext.getCamelContext()).andStubReturn(camelContext);
        replay(routeContext);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForErrorHandler(routeContext, processor, errorHandlerFactory);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=errorhandlers,name="));
        assertTrue(name.toString(), name.toString().endsWith(")"));
    }
    
    @Test
    public void testObjectNameForAwareErrorHandlerFactory() throws Exception {
        expect(routeContext.getCamelContext()).andStubReturn(camelContext);
        replay(routeContext);
        
        expect(awareErrorHandlerFactory.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareErrorHandlerFactory);

        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForErrorHandler(routeContext, processor, awareErrorHandlerFactory);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=errorhandlers,name="));
        assertTrue(name.toString(), name.toString().endsWith(",testKey=\"testValue\""));
    }
    
    @Test
    public void testObjectNameForProcessor() throws Exception {
        expect(definition.getId()).andStubReturn("testProcessor");
        replay(definition);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForProcessor(camelContext, processor, definition);

        assertEquals("org.apache.camel:context=camelContext,type=processors,name=\"testProcessor\"", name.toString());
    }
    
    @Test
    public void testObjectNameForAwareProcessor() throws Exception {
        expect(definition.getId()).andStubReturn("testProcessor");
        replay(definition);
        
        expect(awareProcessor.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareProcessor);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForProcessor(camelContext, awareProcessor, definition);

        assertEquals("org.apache.camel:context=camelContext,type=processors,name=\"testProcessor\",testKey=\"testValue\"", name.toString());
    }
    
    @Test
    public void testObjectNameForRoute() throws Exception {
        expect(endpoint.getCamelContext()).andStubReturn(camelContext);
        replay(endpoint);
        
        expect(route.getEndpoint()).andStubReturn(endpoint);
        expect(route.getId()).andStubReturn("testRoute");
        replay(route);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForRoute(route);

        assertEquals("org.apache.camel:context=camelContext,type=routes,name=\"testRoute\"", name.toString());
    }
    
    @Test
    public void testObjectNameForAwareRoute() throws Exception {
        expect(endpoint.getCamelContext()).andStubReturn(camelContext);
        replay(endpoint);
        
        expect(awareRoute.getEndpoint()).andStubReturn(endpoint);
        expect(awareRoute.getId()).andStubReturn("testRoute");
        expect(awareRoute.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareRoute);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForRoute(awareRoute);

        assertEquals("org.apache.camel:context=camelContext,type=routes,name=\"testRoute\",testKey=\"testValue\"", name.toString());
    }
    
    @Test
    public void testObjectnameForConsumer() throws Exception {
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForConsumer(camelContext, consumer);

        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=consumers,name="));
        assertTrue(name.toString(), name.toString().endsWith(")"));
    }
    
    @Test
    public void testObjectnameForAwareConsumer() throws Exception {
        expect(awareConsumer.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareConsumer);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForConsumer(camelContext, awareConsumer);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=consumers,name="));
        assertTrue(name.toString(), name.toString().endsWith(",testKey=\"testValue\""));
    }
    
    @Test
    public void testObjectNameForProducer() throws Exception {
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForProducer(camelContext, producer);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=producers,name="));
        assertTrue(name.toString(), name.toString().endsWith(")"));
    }
    
    @Test
    public void testObjectNameForAwareProducer() throws Exception {
        expect(awareProducer.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareProducer);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForProducer(camelContext, awareProducer);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=producers,name="));
        assertTrue(name.toString(), name.toString().endsWith(",testKey=\"testValue\""));
    }
    
    @Test
    public void testObjectNameForTracer() throws Exception {
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForTracer(camelContext, tracer);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=tracer,name="));
    }
    
    @Test
    public void testObjectNameForAwareTracer() throws Exception {
        expect(awareTracer.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareTracer);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForTracer(camelContext, awareTracer);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=tracer,name="));
        assertTrue(name.toString(), name.toString().endsWith(",testKey=\"testValue\""));

    }
    
    @Test
    public void testObjectNameForService() throws Exception {
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForService(camelContext, service);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=services,name="));
        assertTrue(name.toString(), name.toString().endsWith(")"));
    }
    
    @Test
    public void testObjectNameForAwareService() throws Exception {
        expect(awareService.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareService);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForService(camelContext, awareService);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=services,name="));
        assertTrue(name.toString(), name.toString().endsWith(",testKey=\"testValue\""));
    }
    
    @Test
    public void testObjectNameForEventNotifier() throws Exception {
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForEventNotifier(camelContext, eventNotifier);
        
        // auto-generate id
        assertTrue(name.toString(), name.toString().startsWith("org.apache.camel:context=camelContext,type=eventnotifiers,name=EventNotifier("));
        assertTrue(name.toString(), name.toString().endsWith(")"));
    }
    
    @Test
    public void testObjectNameForAwareEventNotifier() throws Exception {
        expect(awareEventNotifier.getManagedAttributes()).andStubReturn(createManagedAttributes());
        replay(awareEventNotifier);
        
        ObjectName name = defaultManagementNamingStrategy.getObjectNameForEventNotifier(camelContext, awareEventNotifier);
        
        // class name of a mock is a dynamic proxy class, not fixed per test
        assertEquals("org.apache.camel:context=camelContext,type=eventnotifiers,name=EventNotifier,testKey=\"testValue\"",
                name.toString());
    }
    
    private Map<String, String> createManagedAttributes() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("testKey", "testValue");
        return attributes;
    }
    
    public static interface ManagementNamingStrategyAwareComponent
    extends Component, ManagementNamingStrategyAware {
    }

    public static interface ManagementNamingStrategyAwareEndpoint
    extends Endpoint, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareDataFormat
    extends DataFormat, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareErrorHandlerFactory
    extends ErrorHandlerFactory, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareProcessor
    extends Processor, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareRoute
    extends Route, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareConsumer
    extends Consumer, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareProducer
    extends Producer, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareTracer
    extends InterceptStrategy, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareService
    extends Service, ManagementNamingStrategyAware {
    }
    
    public static interface ManagementNamingStrategyAwareEventNotifier
    extends EventNotifier, ManagementNamingStrategyAware {
    }
}
