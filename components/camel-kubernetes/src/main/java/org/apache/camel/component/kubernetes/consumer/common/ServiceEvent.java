package org.apache.camel.component.kubernetes.consumer.common;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Watcher.Action;

public class ServiceEvent {
    private io.fabric8.kubernetes.client.Watcher.Action action;
    
    private Service service;

	public ServiceEvent(Action action, Service service) {
		super();
		this.action = action;
		this.service = service;
	}

	public io.fabric8.kubernetes.client.Watcher.Action getAction() {
		return action;
	}

	public void setAction(io.fabric8.kubernetes.client.Watcher.Action action) {
		this.action = action;
	}

	public Service getService() {
		return service;
	}

	public void setService(Service service) {
		this.service = service;
	}
}
