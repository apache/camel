package org.apache.camel.component.kubernetes.consumer.common;

import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.client.Watcher.Action;

public class ReplicationControllerEvent {
    private io.fabric8.kubernetes.client.Watcher.Action action;
    
    private ReplicationController replicationController;

	public ReplicationControllerEvent(Action action, ReplicationController rc) {
		super();
		this.action = action;
		this.replicationController = rc;
	}

	public io.fabric8.kubernetes.client.Watcher.Action getAction() {
		return action;
	}

	public void setAction(io.fabric8.kubernetes.client.Watcher.Action action) {
		this.action = action;
	}

	public ReplicationController getReplicationController() {
		return replicationController;
	}

	public void setReplicationController(ReplicationController replicationController) {
		this.replicationController = replicationController;
	}
}
