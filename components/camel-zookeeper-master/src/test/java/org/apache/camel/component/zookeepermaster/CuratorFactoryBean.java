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
package org.apache.camel.component.zookeepermaster;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;

/**
 * Spring {@link FactoryBean} to make using {@link CuratorFramework} easier to setup
 * in Spring XML files also.
 */
public class CuratorFactoryBean implements FactoryBean<CuratorFramework>, DisposableBean {

    private static final transient Logger LOG = LoggerFactory.getLogger(CuratorFactoryBean.class);

    private String connectString = "localhost:2181";
    private int timeout = 30000;
    private CuratorFramework curator;

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    // FactoryBean interface
    //-------------------------------------------------------------------------
    public CuratorFramework getObject() throws Exception {
        LOG.debug("Connecting to ZooKeeper on " + connectString);

        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
            .connectString(connectString)
            .retryPolicy(new ExponentialBackoffRetry(5, 10))
            .connectionTimeoutMs(getTimeout());

        this.curator = builder.build();
        LOG.debug("Starting curator " + curator);
        curator.start();
        return curator;
    }

    public Class<?> getObjectType() {
        return CuratorFramework.class;
    }

    public boolean isSingleton() {
        return true;
    }

    public void destroy() throws Exception {
        if (curator != null) {
            // Note we cannot use zkClient.close()
            // since you cannot currently close a client which is not connected
            curator.close();
            curator = null;
        }

    }
}
