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
package org.apache.camel.component.google.pubsub.integration;

import java.io.File;

import com.google.api.services.pubsub.Pubsub;
import org.apache.camel.component.google.pubsub.GooglePubsubConnectionFactory;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.junit.Test;

public class PubsubConnectionFactoryTest extends PubsubTestSupport {

    /**
     * Testing Credentials File only,
     * the explicitly set Service Account and Key are tested everywhere else.
     *
     * A section of the test is disabled by default as it relies on
     *
     *  - a valid credentials file
     *  - a valid project
     *
     * and therefore can not be tested with the PubSub Emulator
     *
     * Defaults Option is not tested.
     *
     * @throws Exception
     */
    @Test
    public void testCredentialsFile() throws Exception {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("camel-pubsub-component.json").getFile());

        GooglePubsubConnectionFactory cf = new GooglePubsubConnectionFactory()
                .setCredentialsFileLocation(file.getAbsolutePath())
                .setServiceURL(SERVICE_URL);

        Pubsub pubsub = cf.getDefaultClient();

        String query = String.format("projects/%s", PROJECT_ID);
        // [ DEPENDS on actual project being available]
        /*
        pubsub.projects()
              .topics()
              .list(query)
              .execute();

        */
    }
}
