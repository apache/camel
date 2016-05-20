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
package org.apache.camel.component.telegram.util;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.component.telegram.TelegramService;
import org.apache.camel.component.telegram.TelegramServiceProvider;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.mockito.Mockito;

/**
 * A support test class for Telegram tests.
 */
public class TelegramTestSupport extends CamelTestSupport {

    /**
     * Indicates whether the {@code TelegramService} has been mocked during last test.
     */
    private boolean telegramServiceMocked;

    /**
     * Restores the status of {@code TelegramServiceProvider} if it has been mocked.
     */
    @After
    public void tearDown() {
        if (telegramServiceMocked) {
            TelegramServiceProvider.get().restoreDefaultService();
            this.telegramServiceMocked = false;
        }
    }

    /**
     * Setup an alternative mock {@code TelegramService} in the {@code TelegramServiceProvider} and return it.
     *
     * @return the mock service
     */
    public TelegramService mockTelegramService() {
        TelegramService mockService = Mockito.mock(TelegramService.class);
        TelegramServiceProvider.get().setAlternativeService(mockService);
        this.telegramServiceMocked = true;

        return mockService;
    }

    /**
     * Retrieves the currently mocked {@code TelegramService}.
     *
     * @return the current mock of the telegram service
     */
    public TelegramService currentMockService() {
        return TelegramServiceProvider.get().getAlternativeService();
    }

    /**
     * Retrieves a response from a JSON file on classpath.
     *
     * @param fileName the filename in the classpath
     * @param clazz the target class
     * @param <T> the type of the returned object
     * @return the object representation of the JSON file
     */
    public <T> T getJSONResource(String fileName, Class<T> clazz) {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(fileName)) {
            T value = mapper.readValue(stream, clazz);
            return value;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to load file " + fileName, e);
        }
    }

}
