package org.apache.camel.graalvm;

import java.util.Map;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.TypeConverter;
import org.apache.camel.graalvm.FastExecutorServiceManager;
import org.apache.camel.graalvm.FastTypeConverterRegistry;
import org.apache.camel.graalvm.NoManagementStrategy;
import org.apache.camel.graalvm.NoShutdownStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultInjector;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.impl.SimpleUuidGenerator;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.CaseInsensitiveMap;
import org.apache.camel.util.EventHelper;

public class FastCamelContext extends DefaultCamelContext {

    public FastCamelContext(SimpleRegistry registry) {
        super(registry);
    }

    @Override
    public void addRoutes(final RoutesBuilder builder) throws Exception {
        builder.addRoutesToCamelContext(this);
    }

    @Override
    protected synchronized void doStart() throws Exception {
        try {
            doStartCamel();
        } catch (Exception e) {
            // fire event that we failed to start
            EventHelper.notifyCamelContextStartupFailed(this, e);
            // rethrow cause
            throw e;
        }
    }

    @Override
    protected Injector createInjector() {
        return new DefaultInjector(this);
    }

    @Override
    protected UuidGenerator createDefaultUuidGenerator() {
        return new SimpleUuidGenerator();
    }

    @Override
    protected ManagementStrategy createManagementStrategy() {
        return new NoManagementStrategy();
    }

    @Override
    protected TypeConverter createTypeConverter() {
        FastTypeConverterRegistry answer
                = new FastTypeConverterRegistry(this, getPackageScanClassResolver(), getInjector(), getDefaultFactoryFinder());
        setTypeConverterRegistry(answer);
        return answer;
    }

//    @Override
//    protected ManagementNameStrategy createManagementNameStrategy() {
//        return null;
//    }

//    protected HeadersMapFactory createHeadersMapFactory() {
//        return new HeadersMapFactory() {
//            @Override
//            public Map<String, Object> newMap() {
//                return new CaseInsensitiveMap();
//            }
//
//            @Override
//            public Map<String, Object> newMap(Map<String, Object> map) {
//                return new CaseInsensitiveMap(map);
//            }
//
//            @Override
//            public boolean isInstanceOf(Map<String, Object> map) {
//                return map instanceof CaseInsensitiveMap;
//            }
//
//            @Override
//            public boolean isCaseInsensitive() {
//                return true;
//            }
//        };
//    }

//    public synchronized String getVersion() {
//        return "2.22.0-SNAPSHOT";
//    }

    @Override
    public Boolean isTypeConverterStatisticsEnabled() {
        return null;
    }

    @Override
    protected ShutdownStrategy createShutdownStrategy() {
        return new NoShutdownStrategy();
    }

//    @Override
//    protected ExecutorServiceManager createExecutorServiceManager() {
//        return new FastExecutorServiceManager(this);
//    }

}
