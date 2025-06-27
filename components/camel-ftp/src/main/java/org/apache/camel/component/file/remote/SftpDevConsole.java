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
package org.apache.camel.component.file.remote;

import java.util.Map;
import java.util.TreeMap;

import com.jcraft.jsch.JSch;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "sftp", displayName = "SFTP", description = "Secure FTP using JSCH")
public class SftpDevConsole extends AbstractDevConsole {

    public SftpDevConsole() {
        super("camel", "sftp", "SFTP", "Secure FTP using JSCH");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        // sort configs
        Map<String, Object> map = new TreeMap<>(String::compareToIgnoreCase);
        map.putAll(JSch.getConfig());

        sb.append("SFTP Configuration\n");
        for (var e : map.entrySet()) {
            String v = e.getValue() != null ? e.getValue().toString() : "";
            sb.append(String.format("\n    %s = %s", e.getKey(), v));
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        // sort configs
        Map<String, Object> map = new TreeMap<>(String::compareToIgnoreCase);
        map.putAll(JSch.getConfig());

        JsonObject jo = new JsonObject();
        for (var e : map.entrySet()) {
            String v = e.getValue() != null ? e.getValue().toString() : "";
            jo.put(e.getKey(), e.getValue());
        }
        root.put("config", jo);

        return root;
    }
}
