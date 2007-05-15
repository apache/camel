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
package org.apache.camel.bam.model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a single business process
 *
 * @version $Revision: $
 */
@Entity
public class ProcessInstance extends TemporalEntity {
    @OneToMany(mappedBy = "process", fetch = FetchType.LAZY, cascade = {CascadeType.ALL})
    private Set<ActivityState> activityStates = new HashSet<ActivityState>();
    private String key;


    /**
     * Returns the activity state for the given activity
     *
     * @param activity the activity to find the state for
     * @return the activity state or null if no state could be found for the
     *         given activity
     */
    public ActivityState getActivityState(Activity activity) {
        for (ActivityState activityState : activityStates) {
            if (activityState.isActivity(activity)) {
                return activityState;
            }
        }
        return null;
    }

    public Set<ActivityState> getActivityStates() {
        return activityStates;
    }

    public void setActivityStates(Set<ActivityState> activityStates) {
        this.activityStates = activityStates;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
