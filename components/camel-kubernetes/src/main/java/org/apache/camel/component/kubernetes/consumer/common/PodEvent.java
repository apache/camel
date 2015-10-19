package org.apache.camel.component.kubernetes.consumer.common;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher.Action;

public class PodEvent {
    private io.fabric8.kubernetes.client.Watcher.Action action;
    
    private Pod pod;

	public PodEvent(Action action, Pod pod) {
		super();
		this.action = action;
		this.pod = pod;
	}

	public io.fabric8.kubernetes.client.Watcher.Action getAction() {
		return action;
	}

	public void setAction(io.fabric8.kubernetes.client.Watcher.Action action) {
		this.action = action;
	}

	public Pod getPod() {
		return pod;
	}

	public void setPod(Pod pod) {
		this.pod = pod;
	}
}
