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
package org.apache.camel.component.gora;

import org.junit.Test;

/**
 * GORA Configuration Tests
 */
public class GoraConfigurationTest {

    @Test(expected = IllegalArgumentException.class)
    public void setKeyClassClassShouldThrowExceptionIfNull() {
        final GoraConfiguration conf = new GoraConfiguration();
        conf.setValueClass(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setKeyClassShouldThrowExceptionIfEmpty() {
        final GoraConfiguration conf = new GoraConfiguration();
        conf.setValueClass("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValueClassClassShouldThrowExceptionIfNull() {
        final GoraConfiguration conf = new GoraConfiguration();
        conf.setValueClass(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValueClassClassShouldThrowExceptionIfEmpty() {
        final GoraConfiguration conf = new GoraConfiguration();
        conf.setValueClass("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void setDataStoreClassShouldThrowExceptionIfNull() {
        final GoraConfiguration conf = new GoraConfiguration();
        conf.setDataStoreClass(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setDataStoreClassShouldThrowExceptionIfEmpty() {
        final GoraConfiguration conf = new GoraConfiguration();
        conf.setDataStoreClass("");
    }

    @Test(expected = NullPointerException.class)
    public void setHadoopConfigurationShouldThrowExceptionIfNull() {
        final GoraConfiguration conf = new GoraConfiguration();
        conf.setHadoopConfiguration(null);
    }

}
