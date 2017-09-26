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
package org.apache.camel.component.http.springboot;

import org.apache.camel.CamelContext;
import org.apache.camel.component.http.SSLContextParametersSecureProtocolSocketFactory;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration of global SSL parameters.
 */
@Configuration
@ConditionalOnBean(HttpComponentAutoConfiguration.class)
@AutoConfigureAfter(HttpComponentAutoConfiguration.class)
public class HttpComponentSSLAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HttpComponentSSLAutoConfiguration.class);

    @Bean
    public static HttpSSLPostProcessor cacheAutoConfigurationValidatorPostProcessor(CamelContext context, HttpComponentConfiguration config) {
        return new HttpSSLPostProcessor(context, config);
    }

    private void dummy() {
        // checkstyle issue
    }

    static class HttpSSLPostProcessor implements BeanFactoryPostProcessor {

        private CamelContext context;

        private HttpComponentConfiguration config;

        HttpSSLPostProcessor(CamelContext context, HttpComponentConfiguration config) {
            this.context = context;
            this.config = config;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            try {
                if (config != null && config.getUseGlobalSslContextParameters() != null && config.getUseGlobalSslContextParameters()) {
                    SSLContextParameters globalSSLParams = context.getSSLContextParameters();

                    if (globalSSLParams != null) {
                        ProtocolSocketFactory factory =
                                new SSLContextParametersSecureProtocolSocketFactory(globalSSLParams, context);

                        Protocol.registerProtocol("https",
                                new Protocol(
                                        "https",
                                        factory,
                                        443));
                    }
                }

            } catch (NoUniqueBeanDefinitionException e) {
                LOG.warn("Multiple instance of SSLContextParameters found, skipping configuration");
            } catch (NoSuchBeanDefinitionException e) {
                LOG.debug("No instance of SSLContextParameters found");
            } catch (BeansException e) {
                LOG.warn("Cannot create SSLContextParameters", e);
            }
        }
    }

}