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
package org.apache.camel.jbang.console;

import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "jbang", group = "camel-jbang", displayName = "Camel CLI", description = "Information about Camel CLI")
public class JBangDevConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "The JBang version (only present when known)") String version) {
    }

    public JBangDevConsole() {
        super("camel-jbang", "jbang", "Camel CLI", "Information about Camel CLI");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final StringBuilder sb = new StringBuilder();

        String v = VersionHelper.getJBangVersion();
        if (v != null) {
            sb.append(String.format("JBang: %s", v));
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        Response response = new Response(VersionHelper.getJBangVersion());
        return JsonRecordSupport.toJsonObject(response);
    }
}
