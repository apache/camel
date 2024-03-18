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
package org.apache.camel.builder.component.dsl;

import javax.annotation.processing.Generated;
import org.apache.camel.Component;
import org.apache.camel.builder.component.AbstractComponentBuilder;
import org.apache.camel.builder.component.ComponentBuilder;
import org.apache.camel.component.chat.LangchainChatComponent;

/**
 * Langchain4j Chat component
 * 
 * Generated by camel-package-maven-plugin - do not edit this file!
 */
@Generated("org.apache.camel.maven.packaging.ComponentDslMojo")
public interface LangchainChatComponentBuilderFactory {

    /**
     * langchain 4j chat (camel-langchain-chat)
     * Langchain4j Chat component
     * 
     * Category: ai
     * Since: 4.5
     * Maven coordinates: org.apache.camel:camel-langchain-chat
     * 
     * @return the dsl builder
     */
    static LangchainChatComponentBuilder langchainChat() {
        return new LangchainChatComponentBuilderImpl();
    }

    /**
     * Builder for the langchain 4j chat component.
     */
    interface LangchainChatComponentBuilder
            extends
                ComponentBuilder<LangchainChatComponent> {
        /**
         * Operation in case of Endpoint of type CHAT. value is one the values
         * of org.apache.camel.component.langchain.LangchainChatOperations.
         * 
         * The option is a:
         * &lt;code&gt;org.apache.camel.component.chat.LangchainChatOperations&lt;/code&gt; type.
         * 
         * Default: CHAT_SINGLE_MESSAGE
         * Group: producer
         * 
         * @param chatOperation the value to set
         * @return the dsl builder
         */
        default LangchainChatComponentBuilder chatOperation(
                org.apache.camel.component.chat.LangchainChatOperations chatOperation) {
            doSetProperty("chatOperation", chatOperation);
            return this;
        }
        /**
         * The configuration.
         * 
         * The option is a:
         * &lt;code&gt;org.apache.camel.component.chat.LangchainChatConfiguration&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param configuration the value to set
         * @return the dsl builder
         */
        default LangchainChatComponentBuilder configuration(
                org.apache.camel.component.chat.LangchainChatConfiguration configuration) {
            doSetProperty("configuration", configuration);
            return this;
        }
        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default LangchainChatComponentBuilder lazyStartProducer(
                boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        /**
         * Whether autowiring is enabled. This is used for automatic autowiring
         * options (the option must be marked as autowired) by looking up in the
         * registry to find if there is a single instance of matching type,
         * which then gets configured on the component. This can be used for
         * automatic configuring JDBC data sources, JMS connection factories,
         * AWS Clients, etc.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: true
         * Group: advanced
         * 
         * @param autowiredEnabled the value to set
         * @return the dsl builder
         */
        default LangchainChatComponentBuilder autowiredEnabled(
                boolean autowiredEnabled) {
            doSetProperty("autowiredEnabled", autowiredEnabled);
            return this;
        }
        /**
         * Chat Language Model of type
         * dev.langchain4j.model.chat.ChatLanguageModel.
         * 
         * The option is a:
         * &lt;code&gt;dev.langchain4j.model.chat.ChatLanguageModel&lt;/code&gt;
         * type.
         * 
         * Group: advanced
         * 
         * @param chatModel the value to set
         * @return the dsl builder
         */
        default LangchainChatComponentBuilder chatModel(
                dev.langchain4j.model.chat.ChatLanguageModel chatModel) {
            doSetProperty("chatModel", chatModel);
            return this;
        }
    }

    class LangchainChatComponentBuilderImpl
            extends
                AbstractComponentBuilder<LangchainChatComponent>
            implements
                LangchainChatComponentBuilder {
        @Override
        protected LangchainChatComponent buildConcreteComponent() {
            return new LangchainChatComponent();
        }
        private org.apache.camel.component.chat.LangchainChatConfiguration getOrCreateConfiguration(
                org.apache.camel.component.chat.LangchainChatComponent component) {
            if (component.getConfiguration() == null) {
                component.setConfiguration(new org.apache.camel.component.chat.LangchainChatConfiguration());
            }
            return component.getConfiguration();
        }
        @Override
        protected boolean setPropertyOnComponent(
                Component component,
                String name,
                Object value) {
            switch (name) {
            case "chatOperation": getOrCreateConfiguration((LangchainChatComponent) component).setChatOperation((org.apache.camel.component.chat.LangchainChatOperations) value); return true;
            case "configuration": ((LangchainChatComponent) component).setConfiguration((org.apache.camel.component.chat.LangchainChatConfiguration) value); return true;
            case "lazyStartProducer": ((LangchainChatComponent) component).setLazyStartProducer((boolean) value); return true;
            case "autowiredEnabled": ((LangchainChatComponent) component).setAutowiredEnabled((boolean) value); return true;
            case "chatModel": getOrCreateConfiguration((LangchainChatComponent) component).setChatModel((dev.langchain4j.model.chat.ChatLanguageModel) value); return true;
            default: return false;
            }
        }
    }
}