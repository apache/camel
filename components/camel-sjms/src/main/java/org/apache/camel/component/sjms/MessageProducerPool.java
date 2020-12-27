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
package org.apache.camel.component.sjms;

import javax.jms.JMSException;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MessageProducerResources} pool for {@link SjmsProducer} producers.
 */
class MessageProducerPool extends BasePoolableObjectFactory<MessageProducerResources> {

    private static final Logger LOG = LoggerFactory.getLogger(MessageProducerPool.class);

    private final SjmsProducer sjmsProducer;

    public MessageProducerPool(SjmsProducer sjmsProducer) {
        this.sjmsProducer = sjmsProducer;
    }

    @Override
    public MessageProducerResources makeObject() throws Exception {
        return sjmsProducer.doCreateProducerModel(sjmsProducer.createSession());
    }

    @Override
    public boolean validateObject(MessageProducerResources obj) {
        try {
            obj.getSession().getAcknowledgeMode();
            return true;
        } catch (JMSException ex) {
            LOG.warn("Cannot validate JMS session", ex);
        }
        return false;
    }

    @Override
    public void destroyObject(MessageProducerResources model) throws Exception {
        if (model.getMessageProducer() != null) {
            model.getMessageProducer().close();
        }

        if (model.getSession() != null) {
            try {
                if (model.getSession().getTransacted()) {
                    try {
                        model.getSession().rollback();
                    } catch (Exception e) {
                        // Do nothing. Just make sure we are cleaned up
                    }
                }
                model.getSession().close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
