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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.BeanManager;

import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteEvent;
import org.apache.camel.support.EventNotifierSupport;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

@Vetoed
final class CdiEventNotifier extends EventNotifierSupport {

    private final BeanManager manager;

    private final Annotation[] qualifiers;

    CdiEventNotifier(BeanManager manager, Collection<Annotation> qualifiers) {
        this.manager = manager;
        this.qualifiers = qualifiers.toArray(new Annotation[qualifiers.size()]);
        // TODO: be more fine grained for the kind of events that are emitted depending on the observed event types
    }

    @Override
    public void notify(CamelEvent event) {
        String id = null;

        if (event instanceof RouteEvent) {
            id = ((RouteEvent) event).getRoute().getId();
        }

        if (isNotEmpty(id)) {
            List<Annotation> annotations = new ArrayList<>();
            Collections.addAll(annotations, qualifiers);
            annotations.add(NamedLiteral.of(id));
            manager.getEvent().select(annotations.toArray(new Annotation[0])).fire(event);
        } else {
            manager.getEvent().select(qualifiers).fire(event);
        }
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return true;
    }
}
