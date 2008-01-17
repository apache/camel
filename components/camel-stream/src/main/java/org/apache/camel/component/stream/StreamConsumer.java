/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.stream;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StreamConsumer extends DefaultConsumer<StreamExchange> implements StreamReceiver {

    private static final String TYPES = "in";

    private static final String INVALID_URI = "Invalid uri, valid form: 'stream:{"
                    + TYPES + "}'";

    private static final List<String> TYPES_LIST = Arrays.asList(TYPES.split(","));
    private static final Log log = LogFactory.getLog(StreamConsumer.class);
    Endpoint<StreamExchange> endpoint;
    private Map<String, String> parameters;
    private String uri;
    public StreamConsumer(Endpoint<StreamExchange> endpoint,
                          Processor processor, String uri, Map<String, String> parameters)
                          throws Exception {

        super(endpoint, processor);
        this.endpoint = endpoint;
        this.parameters = parameters;
        validateUri(uri);
        log.debug("### stream consumer created");

    }

    @Override
    protected void doStart() throws Exception {

        super.doStart();
        InputStreamHandler sh = new StdInHandler(this);

        try {

            if (parameters.get("prompt") != null) {
                pass(parameters.get("prompt"));
            }
            sh.consume();

        } catch (ConsumingException e) {
            log.error(e);
            e.printStackTrace();
        }

    }

    public int pass(String s) throws Exception {

        Exchange ex = endpoint.createExchange();

        ex.setIn(new StreamMessage(s));

        getProcessor().process(ex);

        return 0;

    }

    private void validateUri(String uri) throws Exception {

        String[] s = uri.split(":");
        if (s.length < 2) {
            throw new Exception(INVALID_URI);

        }
        String[] t = s[1].split("\\?");

        if (t.length < 1) {
            throw new Exception(INVALID_URI);
        }

        this.uri = t[0].trim();

        if (!TYPES_LIST.contains(this.uri)) {

            throw new Exception(INVALID_URI);

        }
    }

}
