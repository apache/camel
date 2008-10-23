/**
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
package org.apache.camel.guice;

import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.naming.Context;

import com.google.inject.Inject;

import org.apache.camel.Route;
import org.apache.camel.Routes;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.guice.impl.GuiceInjector;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ExchangeConverter;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Registry;

/**
 * The default CamelContext implementation for working with Guice.
 *
 * It is recommended you use this implementation with the
 * <a href="http://code.google.com/p/guiceyfruit/wiki/GuiceyJndi">Guicey JNDI Provider</a>
 * 
 * @version $Revision$
 */
public class GuiceCamelContext extends DefaultCamelContext {
    private final com.google.inject.Injector injector;

    @Inject
    public GuiceCamelContext(com.google.inject.Injector injector) {
        this.injector = injector;
    }

    @PostConstruct
    @Override
    public void start() throws Exception {
        super.start();
    }

    @PreDestroy
    @Override
    public void stop() throws Exception {
        super.stop();
    }

    @Inject
    public void setRouteBuilders(Set<Routes> routeBuilders) throws Exception {
        for (Routes routeBuilder : routeBuilders) {
            addRoutes(routeBuilder);
        }
    }

    @Override
    @Inject(optional = true)
    public void setRoutes(List<Route> routes) {
        super.setRoutes(routes);
    }

    @Override
    @Inject(optional = true)
    public void setRegistry(Registry registry) {
        super.setRegistry(registry);
    }

    @Override
    @Inject(optional = true)
    public void setJndiContext(Context jndiContext) {
        super.setJndiContext(jndiContext);
    }

    @Override
    @Inject(optional = true)
    public void setInjector(Injector injector) {
        super.setInjector(injector);
    }

    @Override
    @Inject(optional = true)
    public void setExchangeConverter(ExchangeConverter exchangeConverter) {
        super.setExchangeConverter(exchangeConverter);
    }

    @Override
    @Inject(optional = true)
    public void setComponentResolver(ComponentResolver componentResolver) {
        super.setComponentResolver(componentResolver);
    }

    @Override
    @Inject(optional = true)
    public void setAutoCreateComponents(boolean autoCreateComponents) {
        super.setAutoCreateComponents(autoCreateComponents);
    }

    @Override
    @Inject(optional = true)
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        super.setErrorHandlerBuilder(errorHandlerBuilder);
    }

    @Override
    @Inject(optional = true)
    public void setInterceptStrategies(List<InterceptStrategy> interceptStrategies) {
        super.setInterceptStrategies(interceptStrategies);
    }

    @Override
    @Inject(optional = true)
    public void setLanguageResolver(LanguageResolver languageResolver) {
        super.setLanguageResolver(languageResolver);
    }

    @Override
    @Inject(optional = true)
    public void setLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {
        super.setLifecycleStrategy(lifecycleStrategy);
    }

    @Override
    @Inject(optional = true)
    public void setTypeConverter(TypeConverter typeConverter) {
        super.setTypeConverter(typeConverter);
    }

    @Override
    protected Injector createInjector() {
        return new GuiceInjector(injector);
    }

    @Override
    protected Registry createRegistry() {
        Context context = createContext();
        return new JndiRegistry(context);
    }

    protected Context createContext() {
        try {
            return (Context) injector.getInstance(Context.class);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

}
