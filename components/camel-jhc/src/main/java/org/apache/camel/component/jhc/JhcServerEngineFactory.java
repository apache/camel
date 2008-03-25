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
package org.apache.camel.component.jhc;

import java.util.HashMap;
import java.util.Map;
import org.apache.http.params.HttpParams;

public final class JhcServerEngineFactory {

    private static Map<Integer, JhcServerEngine> portMap = new HashMap<Integer, JhcServerEngine>();

    private JhcServerEngineFactory() {
        //Utility Class
    }

    public static synchronized JhcServerEngine getJhcServerEngine(final HttpParams params, final int port, final String protocol) {
        JhcServerEngine engine = portMap.get(port);
        // check the engine parament
        if (engine == null) {
            engine = new JhcServerEngine(params, port, protocol.trim());
            portMap.put(port, engine);
        } else {
            if (!engine.getProtocol().equals(protocol.trim())) {
                throw new IllegalArgumentException("Jhc protocol error, the engine's protocol is "
                                                   + engine.getProtocol() + " you want is " + protocol);
            }
        }
        return engine;
    }

}
