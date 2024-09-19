package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class TaskTimeTrackedUpdatedEvent extends Event implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("history_items")
    private List<TimeSpentHistoryItem> historyItems;

    @JsonProperty("data")
    private TaskTimeTrackedUpdatedEventData data;

    public String getTaskId() {
        return taskId;
    }

    public List<TimeSpentHistoryItem> getHistoryItems() {
        return historyItems;
    }

    public TaskTimeTrackedUpdatedEventData getData() {
        return data;
    }

    public TaskTimeTrackedUpdatedEventType getType() {
        if (this.historyItems == null) {
            return TaskTimeTrackedUpdatedEventType.DELETION;
        }

        if (this.data.isCreation()) {
            return TaskTimeTrackedUpdatedEventType.CREATION;
        }

        return TaskTimeTrackedUpdatedEventType.UPDATE;
    }

    @Override
    public String toString() {
        return "TaskTimeTrackedUpdatedEvent{" +
                "taskId='" + taskId + '\'' +
                ", historyItems=" + historyItems +
                ", data=" + data +
                ", webhookId='" + webhookId + '\'' +
                '}';
    }

}
