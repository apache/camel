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
package org.apache.camel.main.xml.spring;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.model.Model;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.core.metrics.StartupStep;

/**
 * Used for parsing and discovering legacy Spring XML <beans> to make it runnable on camel-jbang, and for tooling to
 * migrate this to modern Camel DSL in plain Camel XML or YAML DSL.
 */
public class SpringXmlBeansHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SpringXmlBeansHandler.class);
    private static final Pattern SPRING_PATTERN = Pattern.compile("\\$\\{(.*?)}"); // non-greedy mode

    // when preparing spring-based beans, we may have problems loading classes which are provided with Java DSL
    // that's why some beans should be processed later
    private final List<String> delayedBeans = new LinkedList<>();
    // register some existing beans (the list may change)
    // would be nice to keep the documentation up to date: docs/user-manual/modules/ROOT/pages/camel-jbang.adoc
    private final Set<String> infraBeanNames = Set.of("CamelContext", "MainConfiguration");

    /**
     * Parses the XML documents and discovers spring beans, which will be created by Spring {@link BeanFactory}.
     */
    public void processSpringBeans(
            CamelContext camelContext, MainConfigurationProperties config, final Map<String, Document> xmls) {

        LOG.debug("Loading beans from classic Spring <beans> XML");

        // we _could_ create something like org.apache.camel.spring.spi.ApplicationContextBeanRepository, but
        // wrapping DefaultListableBeanFactory and use it as one of the
        // org.apache.camel.support.DefaultRegistry.repositories, but for now let's use it to populate
        // Spring registry and then copy the beans (whether the scope is)
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.setAllowCircularReferences(true); // for now
        beanFactory.setBeanClassLoader(camelContext.getApplicationContextClassLoader());
        beanFactory.setBeanExpressionResolver((value, beanExpressionContext) -> extractValue(camelContext, value, true));
        camelContext.getRegistry().bind("SpringBeanFactory", beanFactory);

        beanFactory.registerSingleton("CamelContext", camelContext);
        beanFactory.registerSingleton("MainConfiguration", config);
        // ...

        // instead of generating an MX parser for spring-beans.xsd and use it to read the docs, we can simply
        // pass w3c Documents directly to Spring
        final XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(beanFactory);
        xmls.forEach((id, doc) -> {
            reader.registerBeanDefinitions(doc, new AbstractResource() {
                @Override
                public String getFilename() {
                    if (id.startsWith("camel-xml-io-dsl-spring-xml:")) {
                        // this is a camel bean via camel-xml-io-dsl
                        return StringHelper.afterLast(id, ":");
                    }
                    return null;
                }

                @Override
                public String getDescription() {
                    return id;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(new byte[0]);
                }
            });
        });

        // for full interaction between Spring ApplicationContext and its BeanFactory see
        // org.springframework.context.support.AbstractApplicationContext.refresh()
        // see org.springframework.context.support.AbstractApplicationContext.prepareBeanFactory() to check
        // which extra/infra beans are added
        beanFactory.freezeConfiguration();

        List<String> beanNames = Arrays.asList(beanFactory.getBeanDefinitionNames());

        // Trigger initialization of all non-lazy singleton beans...
        LOG.info("Discovered {} Spring XML <beans>", beanNames.size());
        instantiateAndRegisterBeans(camelContext, beanFactory, beanNames);
    }

    /**
     * Invoked at later stage to create and register Spring beans into Camel {@link org.apache.camel.spi.Registry}.
     */
    public void createAndRegisterBeans(CamelContext camelContext) {
        if (delayedBeans.isEmpty()) {
            return;
        }

        DefaultListableBeanFactory beanFactory
                = camelContext.getRegistry().lookupByNameAndType("SpringBeanFactory", DefaultListableBeanFactory.class);

        // we have some beans with classes that we couldn't load before. now, after loading the routes
        // we may have the needed class definitions
        for (String beanName : delayedBeans) {
            BeanDefinition bd = beanFactory.getMergedBeanDefinition(beanName);
            if (bd instanceof AbstractBeanDefinition abd) {
                if (!abd.hasBeanClass()) {
                    Class<?> c = camelContext.getClassResolver().resolveClass(abd.getBeanClassName());
                    abd.setBeanClass(c);
                }
            }
        }

        instantiateAndRegisterBeans(camelContext, beanFactory, delayedBeans);
    }

    private void instantiateAndRegisterBeans(
            CamelContext camelContext, DefaultListableBeanFactory beanFactory, List<String> beanNames) {
        List<String> instantiatedBeanNames = new LinkedList<>();

        for (String beanName : beanNames) {
            BeanDefinition bd = beanFactory.getMergedBeanDefinition(beanName);
            if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                try {
                    if (beanFactory.isFactoryBean(beanName)) {
                        Object bean = beanFactory.getBean(BeanFactory.FACTORY_BEAN_PREFIX + beanName);
                        if (bean instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isEagerInit()) {
                            beanFactory.getBean(beanName);
                            instantiatedBeanNames.add(beanName);
                        }
                    } else {
                        beanFactory.getBean(beanName);
                        instantiatedBeanNames.add(beanName);
                    }
                } catch (CannotLoadBeanClassException ignored) {
                    // we'll try to resolve later
                    delayedBeans.add(beanName);
                }
            }
        }

        // Trigger post-initialization callback for all applicable beans...
        for (String beanName : instantiatedBeanNames) {
            Object singletonInstance = beanFactory.getSingleton(beanName);
            if (singletonInstance instanceof SmartInitializingSingleton smartSingleton) {
                StartupStep smartInitialize = beanFactory.getApplicationStartup()
                        .start("spring.beans.smart-initialize")
                        .tag("beanName", beanName);
                smartSingleton.afterSingletonsInstantiated();
                smartInitialize.end();
            }
        }

        for (String name : instantiatedBeanNames) {
            if (infraBeanNames.contains(name)) {
                continue;
            }
            BeanDefinition def = beanFactory.getBeanDefinition(name);
            if (def.isSingleton()) {
                // just grab the singleton and put into registry
                camelContext.getRegistry().bind(name, beanFactory.getBean(name));
            } else {
                // rely on the bean factory to implement prototype scope
                camelContext.getRegistry().bind(name, (Supplier<Object>) () -> beanFactory.getBean(name));
            }

            addBeanToCamelModel(camelContext, name, def);
        }
    }

    private void addBeanToCamelModel(CamelContext camelContext, String name, BeanDefinition def) {
        // register bean into model (as a BeanRegistry that allows Camel DSL to know about these beans)
        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        if (model != null) {
            RegistryBeanDefinition rrd = new RegistryBeanDefinition();
            if (def instanceof GenericBeanDefinition gbd) {
                // set camel resource to refer to the source file
                Resource res = gbd.getResource();
                if (res != null) {
                    String fn = res.getFilename();
                    if (fn != null) {
                        rrd.setResource(camelContext.getCamelContextExtension().getContextPlugin(ResourceLoader.class)
                                .resolveResource("file:" + fn));
                    }
                }
            }
            rrd.setType(def.getBeanClassName());
            rrd.setName(name);
            model.addRegistryBean(rrd);

            // constructor arguments
            ConstructorArgumentValues ctr = def.getConstructorArgumentValues();
            StringJoiner sj = new StringJoiner(", ");
            for (ConstructorArgumentValues.ValueHolder v : ctr.getIndexedArgumentValues().values()) {
                Object val = v.getValue();
                if (val instanceof TypedStringValue tsv) {
                    sj.add("'" + extractValue(camelContext, tsv.getValue(), false) + "'");
                } else if (val instanceof BeanReference br) {
                    sj.add("'#bean:" + extractValue(camelContext, br.getBeanName(), false) + "'");
                }
            }
            if (sj.length() > 0) {
                rrd.setType("#class:" + def.getBeanClassName() + "(" + sj + ")");
            }
            // property values
            if (def.hasPropertyValues()) {
                Map<String, Object> properties = new LinkedHashMap<>();
                rrd.setProperties(properties);

                MutablePropertyValues values = def.getPropertyValues();
                for (PropertyValue v : values) {
                    String key = v.getName();
                    PropertyValue src = v.getOriginalPropertyValue();
                    Object val = src.getValue();
                    if (val instanceof TypedStringValue tsv) {
                        properties.put(key, extractValue(camelContext, tsv.getValue(), false));
                    } else if (val instanceof BeanReference br) {
                        properties.put(key, "#bean:" + extractValue(camelContext, br.getBeanName(), false));
                    } else if (val instanceof List) {
                        int i = 0;
                        Iterator<?> it = ObjectHelper.createIterator(val);
                        while (it.hasNext()) {
                            String k = key + "[" + i + "]";
                            val = it.next();
                            if (val instanceof TypedStringValue tsv) {
                                properties.put(k, extractValue(camelContext, tsv.getValue(), false));
                            } else if (val instanceof BeanReference br) {
                                properties.put(k, "#bean:" + extractValue(camelContext, br.getBeanName(), false));
                            }
                            i++;
                        }
                    } else if (val instanceof Map) {
                        Map<TypedStringValue, Object> map = (Map) val;
                        for (Map.Entry<TypedStringValue, Object> entry : map.entrySet()) {
                            String k = key + "[" + entry.getKey().getValue() + "]";
                            val = entry.getValue();
                            if (val instanceof TypedStringValue tsv) {
                                properties.put(k, extractValue(camelContext, tsv.getValue(), false));
                            } else if (val instanceof BeanReference br) {
                                properties.put(k, "#bean:" + extractValue(camelContext, br.getBeanName(), false));
                            }
                        }
                    }
                }
            }
        }
    }

    protected String extractValue(CamelContext camelContext, String val, boolean resolve) {
        // spring placeholder prefix
        if (val != null && val.contains("${")) {
            Matcher matcher = SPRING_PATTERN.matcher(val);
            while (matcher.find()) {
                String replace = "{{" + matcher.group(1) + "}}";
                val = matcher.replaceFirst(replace);
                // we changed so reset matcher so it can find more
                matcher.reset(val);
            }
        }

        if (resolve && camelContext != null) {
            // if running camel then resolve property placeholders from beans
            val = camelContext.resolvePropertyPlaceholders(val);
        }
        return val;
    }

}
