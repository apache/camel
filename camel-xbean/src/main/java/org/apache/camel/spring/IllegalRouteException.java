package org.apache.camel.spring;

public class IllegalRouteException extends IllegalArgumentException {

	private static final long serialVersionUID = -2166507687211986107L;
	private final String actionName;
	private final String previousAction;

	public IllegalRouteException(String actionName, String previousAction) {
		super("Illegal route.");
		this.actionName = actionName;
		this.previousAction = previousAction;
	}

	@Override
	public String getMessage() {
		String errorContext = previousAction==null ? "as the starting action." : "after action '"+previousAction+"'.";
		return super.getMessage() + "The action '"+actionName+"' cannot be used "+errorContext;
	}
}
