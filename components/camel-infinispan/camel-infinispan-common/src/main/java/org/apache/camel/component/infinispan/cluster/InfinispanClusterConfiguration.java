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
package org.apache.camel.component.infinispan.cluster;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.infinispan.InfinispanConfiguration;

public abstract class InfinispanClusterConfiguration<C extends InfinispanConfiguration> implements Cloneable {
    private final C configuration;
    private long lifespan;
    private TimeUnit lifespanTimeUnit;

    protected InfinispanClusterConfiguration(C configuration) {
        this.configuration = configuration;
        this.lifespan = 30;
        this.lifespanTimeUnit = TimeUnit.SECONDS;
    }

    // ***********************************************
    // Properties
    // ***********************************************

    public long getLifespan() {
        return lifespan;
    }

    public void setLifespan(long lifespan) {
        this.lifespan = lifespan;
    }

    public TimeUnit getLifespanTimeUnit() {
        return lifespanTimeUnit;
    }

    public void setLifespanTimeUnit(TimeUnit lifespanTimeUnit) {
        this.lifespanTimeUnit = lifespanTimeUnit;
    }

    public void setConfigurationUri(String configurationUri) {
        configuration.setConfigurationUri(configurationUri);
    }

    public C getConfiguration() {
        return configuration;
    }

    // ***********************************************
    //
    // ***********************************************

    @Override
    public InfinispanClusterConfiguration clone() {
        try {
            return (InfinispanClusterConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
