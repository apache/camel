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
package org.apache.camel.script.osgi;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import javax.script.ScriptEngineFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ActivatorTest {

    public static final Logger LOG = LoggerFactory.getLogger(ActivatorTest.class);

    private Bundle mockBundle;

    @Before
    public void mockBundle() throws ClassNotFoundException {
        mockBundle = Mockito.mock(Bundle.class);
        when(mockBundle.loadClass(anyString())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return ActivatorTest.class.getClassLoader().loadClass(invocation.getArguments()[0].toString());
            }
        });
    }

    @Test
    public void findScriptEngines() throws IOException {
        Enumeration<URL> urls = getClass().getClassLoader().getResources(Activator.META_INF_SERVICES_DIR + "/" + Activator.SCRIPT_ENGINE_SERVICE_FILE);
        assertThat(urls.hasMoreElements(), is(true));
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            LOG.info("Found {}", url);
            System.out.println("Found: " + url);
            Activator.BundleScriptEngineResolver resolver = new Activator.BundleScriptEngineResolver(mockBundle, url);
            ScriptEngineFactory factory = ReflectionTestUtils.invokeMethod(resolver, "getFactory");
            System.out.println("Factory: " + factory);
            assertNotNull(factory);
        }
    }

}
