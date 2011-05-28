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
package org.apache.camel.component.hdfs;

import java.net.URL;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.hadoop.fs.FsUrlStreamHandlerFactory;

public class HdfsComponent extends DefaultComponent {

    static {
        URL.setURLStreamHandlerFactory(new FsUrlStreamHandlerFactory());
    }

    public HdfsComponent() {
    }

    public HdfsComponent(CamelContext context) {
        super(context);
    }

    @SuppressWarnings("unchecked")
    protected final Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        HdfsEndpoint hdfsEndpoint = new HdfsEndpoint(uri, this.getCamelContext());
        setProperties(hdfsEndpoint.getConfig(), parameters);
        return hdfsEndpoint;
    }

}
