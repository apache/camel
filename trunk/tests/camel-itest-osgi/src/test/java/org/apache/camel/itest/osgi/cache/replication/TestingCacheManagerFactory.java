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
package org.apache.camel.itest.osgi.cache.replication;

import java.io.InputStream;

import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.Topic;
import javax.jms.TopicConnection;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.distribution.jms.AcknowledgementMode;
import net.sf.ehcache.distribution.jms.JMSCacheManagerPeerProvider;

import org.apache.camel.component.cache.CacheManagerFactory;

public class TestingCacheManagerFactory extends CacheManagerFactory {
    private String xmlName;

    //Only for testing purpose, normally not needed
    private CacheManager cacheManager;

    private Topic replicationTopic;

    private Queue getQueue;

    private TopicConnection replicationTopicConnection;

    private QueueConnection getQueueConnection;

    public TestingCacheManagerFactory(String xmlName, 
            TopicConnection replicationTopicConnection, Topic replicationTopic, 
            QueueConnection getQueueConnection, Queue getQueue) {
        this.xmlName = xmlName;
        this.replicationTopicConnection = replicationTopicConnection;
        this.replicationTopic = replicationTopic;
        this.getQueue = getQueue;
        this.getQueueConnection = getQueueConnection;
    }

    @Override
    protected synchronized CacheManager createCacheManagerInstance() {
        //Singleton- only for testing purpose, normally not needed
        if (cacheManager == null) {
            cacheManager = new WrappedCacheManager(getClass().getResourceAsStream(xmlName));
        }

        return cacheManager;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public class WrappedCacheManager extends CacheManager {
        public WrappedCacheManager(InputStream xmlConfig) {
            super(xmlConfig);
            JMSCacheManagerPeerProvider jmsCMPP = new JMSCacheManagerPeerProvider(this,
                            replicationTopicConnection,
                            replicationTopic,
                            getQueueConnection,
                            getQueue,
                            AcknowledgementMode.AUTO_ACKNOWLEDGE,
                            true);
            cacheManagerPeerProviders.put(jmsCMPP.getScheme(), jmsCMPP);
            jmsCMPP.init();
        }
    }
}