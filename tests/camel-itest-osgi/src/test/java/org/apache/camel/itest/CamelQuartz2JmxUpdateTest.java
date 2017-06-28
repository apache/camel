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
package org.apache.camel.itest;

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.karaf.AbstractFeatureTest;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

/**
 * CAMEL-11471: Unable to update the cron details from Quartz scheduler MBean
 */
@RunWith(PaxExam.class)
public class CamelQuartz2JmxUpdateTest extends AbstractFeatureTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CamelQuartz2JmxUpdateTest.class);

    @Test
    public void testUpdateCronDetails() throws Exception {
        // install camel-quartz2 here as 'wrap:' is not available at boot time
        installCamelFeature("camel-quartz2");

        // install the camel blueprint xml file we use in this test
        URL url = ObjectHelper.loadResourceAsURL("org/apache/camel/itest/CamelQuartz2JmxUpdateTest.xml",
            CamelQuartz2JmxUpdateTest.class.getClassLoader());
        installBlueprintAsBundle("CamelQuartz2JmxUpdateTest", url, true);

        // lookup Camel from OSGi
        CamelContext camel = getOsgiService(bundleContext, CamelContext.class);

        // test camel
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello World");
        mock.assertIsSatisfied(5000);

        doUpdateCronDetails();
    }

    private void doUpdateCronDetails() throws Exception {
        String trigger = "myTimer";
        String group = "myGroup";
        String cronExpression = "0 * * * * ?";

        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> objectNames = mBeanServer.queryNames(
            new ObjectName("quartz:type=QuartzScheduler,name=*,instance=NON_CLUSTERED"), null);
        assertFalse("There should be a quartz scheduler MBean", objectNames.isEmpty());

        ObjectName oName = objectNames.stream().findFirst().get();
        LOGGER.info("Scheduler MBean: {}", oName);

        CompositeData triggerData = (CompositeData) mBeanServer.invoke(oName, "getTrigger",
            new Object[]{trigger, group},
            new String[]{"java.lang.String", "java.lang.String"});
        String jobName = (String) triggerData.get("jobName");
        String jobGroup = (String) triggerData.get("jobGroup");
        CompositeData jobData = (CompositeData) mBeanServer.invoke(oName, "getJobDetail",
            new Object[]{jobName, jobGroup},
            new String[]{"java.lang.String", "java.lang.String"});

        String original = getCronExpression(jobData);
        assertNotEquals("make sure original cron is different", cronExpression, original);

        Map<String, Object> jobInfo = createJobInfo(jobName, jobGroup, cronExpression, jobData);
        Map<String, Object> triggerInfo = createTriggerInfo(trigger, group, cronExpression, jobName, jobGroup);

        // update trigger
        mBeanServer.invoke(oName, "scheduleBasicJob",
            new Object[]{jobInfo, triggerInfo},
            new String[]{"java.util.Map", "java.util.Map"});

        // assert job details updated
        CompositeData jobData2 = (CompositeData) mBeanServer.invoke(oName, "getJobDetail",
            new Object[]{jobName, jobGroup},
            new String[]{"java.lang.String", "java.lang.String"});
        String updated = getCronExpression(jobData2);
        assertEquals("cron should be updated", cronExpression, updated);
    }

    private String getCronExpression(CompositeData jobData) {
        TabularData jobDataMap = (TabularData) jobData.get("jobDataMap");
        CompositeData cron = jobDataMap.get(new String[]{"CamelQuartzTriggerCronExpression"});
        Iterator it = cron.values().iterator();
        it.next();
        return (String) it.next();
    }

    private Map<String, Object> createJobInfo(String jobName, String jobGroup, String cronExpression,
                                              CompositeData jobData) {
        Map<String, Object> jobInfo = new HashMap<>();
        jobInfo.put("name", jobName);
        jobInfo.put("group", jobGroup);
        if (jobData.get("description") != null) {
            jobInfo.put("description", jobData.get("description"));
        }
        jobInfo.put("jobClass", jobData.get("jobClass"));
        jobInfo.put("durability", jobData.get("durability"));
        jobInfo.put("shouldRecover", jobData.get("shouldRecover"));

        Map<String, Object> jobDataMap = new HashMap<>();
        TabularData tJobDataMap = (TabularData) jobData.get("jobDataMap");
        for (Object cKey : tJobDataMap.keySet()) {
            Object key = ((List) cKey).get(0);
            CompositeData cd = tJobDataMap.get(new Object[]{key});
            if (cd != null) {
                Iterator it = cd.values().iterator();
                String tKey = (String) it.next();
                Object tValue = it.next();
                jobDataMap.put(tKey, tValue);
            }
        }
        jobDataMap.put("CamelQuartzTriggerType", "cron");
        jobDataMap.put("CamelQuartzTriggerCronExpression", cronExpression);
        jobInfo.put("jobDataMap", jobDataMap);
        return jobInfo;
    }

    private Map<String, Object> createTriggerInfo(String trigger, String group, String cronExpression,
                                                  String jobName, String jobGroup) {
        Map<String, Object> triggerInfo = new HashMap<>();
        triggerInfo.put("cronExpression", cronExpression);
        triggerInfo.put("name", trigger);
        triggerInfo.put("group", group);
        triggerInfo.put("jobName", jobName);
        triggerInfo.put("jobGroup", jobGroup);
        triggerInfo.put("misfireInstruction", 1);
        return triggerInfo;
    }

    @Configuration
    public Option[] configure() {
        return configure("camel-test-karaf");
    }

}
