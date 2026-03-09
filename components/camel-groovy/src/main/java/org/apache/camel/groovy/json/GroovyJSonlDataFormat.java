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
package org.apache.camel.groovy.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import groovy.util.Node;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;

@Dataformat("groovyJson")
public class GroovyJSonlDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private boolean prettyPrint = true;

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        if (graph instanceof Node n) {
            graph = NodeToJsonHelper.nodeToJson(n);
        }
        if (graph instanceof Map map) {
            serialize(map, stream);
        } else {
            // optional jackson support (TODO: jackson3)
            if (graph.getClass().getName().startsWith("com.fasterxml.jackson.databind")) {
                var map = exchange.getContext().getTypeConverter().convertTo(Map.class, exchange, graph);
                serialize(map, stream);
            } else {
                byte[] arr = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, graph);
                stream.write(arr);
            }
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        JsonSlurper parser = new JsonSlurper();
        return parser.parse(stream);
    }

    @Override
    public String getDataFormatName() {
        return "groovyJson";
    }

    private void serialize(Map map, OutputStream stream) throws IOException {
        String out = JsonOutput.toJson(map);
        out = prettyPrint ? JsonOutput.prettyPrint(out) : JsonOutput.toJson(out);
        stream.write(out.getBytes());
    }

}
