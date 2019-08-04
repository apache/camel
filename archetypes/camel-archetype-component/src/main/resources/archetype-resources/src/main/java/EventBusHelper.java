## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
package ${package};

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Used for demonstrations purpose to simulate some external system event bus/broker, where messages are sent to, and
 * this component can consume from.
 */
public class EventBusHelper {

    // TODO: Delete me when you implementy your custom component

    private static EventBusHelper INSTANCE;

    final private Set<Consumer> subscribers = ConcurrentHashMap.newKeySet();

    private EventBusHelper(){ }

    public static EventBusHelper getInstance(){
        if (INSTANCE == null){
            INSTANCE = new EventBusHelper();
        }

        return INSTANCE;
    }

    public <T> void subscribe(final Consumer<T> subscriber) {
        subscribers.add(subscriber);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(final T event){
        // Notify all subscribers
        subscribers.forEach(consumer -> publishSingleEvent(event, consumer));
    }

    private <T> void publishSingleEvent(final T event, final Consumer<T> subscriber){
        subscriber.accept(event);
    }

}