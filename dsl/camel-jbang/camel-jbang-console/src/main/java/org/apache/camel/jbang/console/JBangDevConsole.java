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

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("jbang")
public class JBangDevConsole extends AbstractDevConsole {

    public JBangDevConsole() {
        super("camel", "jbang", "Camel JBang", "Information about Camel JBang");
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
        JsonObject root = new JsonObject();
        String v = VersionHelper.getJBangVersion();
        if (v != null) {
            root.put("version", v);
        }
        return root;
    }
}
