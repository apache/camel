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
package org.apache.camel.component.atomix.ha;

import io.atomix.Atomix;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.ha.AbstractCamelCluster;

/**
 * TODO: Dummy implementation for testing purpose
 */
public class AtomixCluster extends AbstractCamelCluster<AtomixClusterView> {
    private final Atomix atomix;

    public AtomixCluster(Atomix atomix) {
       this(null, atomix);
    }

    public AtomixCluster(CamelContext camelContext, Atomix atomix) {
        super("camel-atomix", camelContext);

        this.atomix = atomix;
    }

    @Override
    public AtomixClusterView doCreateView(String namespace) throws Exception {
        return new AtomixClusterView(
            this,
            namespace,
            atomix.getGroup(namespace).join()
        );
    }
}
