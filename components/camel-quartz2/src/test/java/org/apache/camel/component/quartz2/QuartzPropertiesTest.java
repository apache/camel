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
package org.apache.camel.component.quartz2;

import java.io.InputStream;
import java.util.Properties;

import org.junit.Test;
import org.quartz.SchedulerException;


public class QuartzPropertiesTest extends BaseQuartzTest {

    private QuartzComponent quartz;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    public void tearDown() throws Exception {
        quartz.stop();
        super.tearDown();
    }

    @Test
    public void testQuartzPropertiesFile() throws Exception {
        quartz = context.getComponent("quartz2", QuartzComponent.class);

        quartz.setPropertiesFile("org/apache/camel/component/quartz2/myquartz.properties");

        quartz.start();

        assertEquals("MyScheduler-" + context.getName(), quartz.getScheduler().getSchedulerName());
        assertEquals("2", quartz.getScheduler().getSchedulerInstanceId());
    }

    @Test
    public void testQuartzPropertiesFileNotFound() throws Exception {
        quartz = context.getComponent("quartz2", QuartzComponent.class);

        quartz.setPropertiesFile("doesnotexist.properties");

        try {
            quartz.start();
            fail("Should have thrown exception");
        } catch (SchedulerException e) {
            assertEquals("Error loading Quartz properties file: doesnotexist.properties", e.getMessage());
        }
    }

    @Test
    public void testQuartzProperties() throws Exception {
        quartz = context.getComponent("quartz2", QuartzComponent.class);

        Properties prop = new Properties();
        InputStream is = context.getClassResolver().loadResourceAsStream("org/apache/camel/component/quartz2/myquartz.properties");
        prop.load(is);
        quartz.setProperties(prop);

        quartz.start();

        assertEquals("MyScheduler-" + context.getName(), quartz.getScheduler().getSchedulerName());
        assertEquals("2", quartz.getScheduler().getSchedulerInstanceId());
    }

}
