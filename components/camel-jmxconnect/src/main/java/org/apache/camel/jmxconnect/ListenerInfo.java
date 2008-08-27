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
package org.apache.camel.jmxconnect;

import javax.management.NotificationFilter;
import javax.management.NotificationListener;

/**
 * @version $Revision$
 */
class ListenerInfo {
    private String id;
    private NotificationListener listener;
    private NotificationFilter filter;
    private Object handback;


    public ListenerInfo(String id, NotificationListener listener, NotificationFilter filter, Object handback) {
        this.id = id;
        this.listener = listener;
        this.filter = filter;
        this.handback = handback;
    }

    /**
     * Is this info a match ?
     *
     * @param l
     * @param f
     * @param handback
     * @return true if a match
     */
    public boolean isMatch(NotificationListener l, NotificationFilter f, Object handback) {
        return listener == listener && filter == filter && handback == handback;
    }

    /**
     * @return Returns the filter.
     */
    public NotificationFilter getFilter() {
        return filter;
    }

    /**
     * @param filter The filter to set.
     */
    public void setFilter(NotificationFilter filter) {
        this.filter = filter;
    }

    /**
     * @return Returns the handback.
     */
    public Object getHandback() {
        return handback;
    }

    /**
     * @param handback The handback to set.
     */
    public void setHandback(Object handback) {
        this.handback = handback;
    }

    /**
     * @return Returns the id.
     */
    public String getId() {
        return id;
    }

    /**
     * @param id The id to set.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return Returns the listener.
     */
    public NotificationListener getListener() {
        return listener;
    }

    /**
     * @param listener The listener to set.
     */
    public void setListener(NotificationListener listener) {
        this.listener = listener;
    }

}