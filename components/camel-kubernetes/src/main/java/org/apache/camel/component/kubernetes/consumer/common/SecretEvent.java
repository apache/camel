package org.apache.camel.component.kubernetes.consumer.common;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Watcher.Action;

public class SecretEvent {
    private io.fabric8.kubernetes.client.Watcher.Action action;
    
    private Secret secret;

	public SecretEvent(Action action, Secret secret) {
		super();
		this.action = action;
		this.secret = secret;
	}

	public io.fabric8.kubernetes.client.Watcher.Action getAction() {
		return action;
	}

	public void setAction(io.fabric8.kubernetes.client.Watcher.Action action) {
		this.action = action;
	}

	public Secret getSecret() {
		return secret;
	}

	public void setSecret(Secret secret) {
		this.secret = secret;
	}
}
