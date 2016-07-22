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
package org.apache.camel.component.bean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.ServiceHelper;

/**
 * Bean {@link org.apache.camel.Producer}
 */
public class BeanProducer extends DefaultAsyncProducer {

    private final BeanProcessor processor;
    private boolean beanStarted;

    public BeanProducer(BeanEndpoint endpoint, BeanProcessor processor) {
        super(endpoint);
        this.processor = processor;
        this.beanStarted = false;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        return processor.process(exchange, callback);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (processor.getBeanHolder() instanceof ConstantBeanHolder) {
            try {
                // Start the bean if it implements Service interface and if cached
                // so meant to be reused
                ServiceHelper.startService(processor.getBean());
                beanStarted = true;
            } catch (NoSuchBeanException e) {
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (beanStarted) {
            try {
                // Stop the bean if it implements Service interface and if cached
                // so meant to be reused
                ServiceHelper.stopService(processor.getBean());
                beanStarted = false;
            } catch (NoSuchBeanException e) {
            }
        }

        super.doStop();
    }
}
