package org.apache.camel.dataformat.bindy.format;

public class FormatException extends Exception {
	
	private static final long serialVersionUID = 2243166587373950715L;

	public FormatException() {
    }

    public FormatException(String message) {
        super(message);
    }

    public FormatException(String message, Throwable cause) {
        super(message, cause);
    }

    public FormatException(Throwable cause) {
        super(cause);
    }

}
