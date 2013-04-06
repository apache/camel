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
package org.apache.camel.example.jmx;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.management.AttributeChangeNotification;
import javax.management.NotificationBroadcasterSupport;

/**
 * Our business logic which also is capable of broadcasting JMX notifications,
 * such as an attribute being changed.
 */
public class SimpleBean extends NotificationBroadcasterSupport implements ISimpleMXBean {

    private static final long serialVersionUID = 1L;
    private int sequence;
    private int tick;

    public void tick() throws Exception {
        tick++;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-dd-MM'T'HH:mm:ss");
        Date date = sdf.parse("2010-07-01T10:30:15");
        long timeStamp = date.getTime();

        AttributeChangeNotification acn = new AttributeChangeNotification(
                this, sequence++, timeStamp, "attribute changed", "stringValue", "string", tick - 1, tick);
        sendNotification(acn);
    }

}
