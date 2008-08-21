package org.apache.camel.component.file.strategy;

import java.io.File;

import org.apache.camel.Expression;
import org.apache.camel.component.file.FileExchange;

/**
 *
 */
public class FileLanguageRenamer implements FileRenamer {

    private static final boolean ON_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private Expression expression;

    public File renameFile(FileExchange exchange, File file) {
        if (expression == null) {
            throw new IllegalArgumentException("Expression is not set");
        }
        File parent = file.getParentFile();

        Object result = expression.evaluate(exchange);
        String name = exchange.getContext().getTypeConverter().convertTo(String.class, result);

        if (ON_WINDOWS && (name.indexOf(":") >= 0 || name.startsWith("//"))) {
            return new File(name);
        }
        return new File(parent, name);
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }
}
