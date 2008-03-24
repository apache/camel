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
package org.apache.camel.component.cxf.util;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.cxf.binding.AbstractBindingFactory;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.DestinationFactory;

public class NullDestinationFactory implements DestinationFactory {

    public Destination getDestination(EndpointInfo ei) throws IOException {
        // setup the endpoint information
        ei.setAddress("local://" + ei.getService().getName().toString() + "/" + ei.getName().getLocalPart());
        // working as the dispatch mode, the binding factory will not add interceptor
        ei.getBinding().setProperty(AbstractBindingFactory.DATABINDING_DISABLED, Boolean.TRUE);
        // do nothing here , just creating a null destination to store the observer
        return new NullDestination();
    }

    public List<String> getTransportIds() {
        return null;
    }

    public Set<String> getUriPrefixes() {
        return null;
    }

}
