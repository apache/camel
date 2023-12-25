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
package org.apache.camel.component.jte;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import gg.jte.CodeResolver;
import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;

/**
 * To load JTE templates from classpath
 */
public class JteCodeResolver implements CodeResolver {

    private final CamelContext camelContext;
    private final Map<String, String> headerTemplates = new ConcurrentHashMap<>();

    public JteCodeResolver(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void addTemplateFromHeader(String exchangeId, String template) {
        if (exchangeId != null && template != null) {
            headerTemplates.put(exchangeId, template);
        }
    }

    @Override
    public String resolve(String name) {
        String answer = headerTemplates.remove(name);
        if (answer == null) {
            InputStream is = camelContext.getClassResolver().loadResourceAsStream(name);
            if (is != null) {
                try {
                    answer = IOHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return answer;
    }

    @Override
    public long getLastModified(String name) {
        return 0;
    }
}
