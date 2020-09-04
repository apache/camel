package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;

/**
 * To use Datasonnet scripts in Camel expressions or predicates.
 */
@Metadata(firstVersion = "3.5.0", label = "language,script", title = "Datasonnet")
@XmlRootElement(name = "datasonnet")
@XmlAccessorType(XmlAccessType.FIELD)
public class DatasonnetExpression extends ExpressionDefinition {

    @XmlAttribute(name = "inputMimeType")
    private String inputMimeType;

    @XmlAttribute(name = "outputMimeType")
    private String outputMimeType;

    @XmlAttribute(name = "resultTypeName")
    private String resultTypeName;

    @XmlTransient
    private Class<?> resultType;

    public DatasonnetExpression() {
    }

    public DatasonnetExpression(String expression) {
        super(expression);
    }

    public DatasonnetExpression(Expression expression) {
        super(expression);
    }

    @Override
    public String getLanguage() {
        return "datasonnet";
    }

    public String getInputMimeType() {
        return inputMimeType;
    }

    /**
     * TODO: 7/21/20 docs
     * 
     * @param inputMimeType docs
     */
    public void setInputMimeType(String inputMimeType) {
        this.inputMimeType = inputMimeType;
    }

    public String getOutputMimeType() {
        return outputMimeType;
    }

    /**
     * TODO: 7/21/20 docs
     * 
     * @param outputMimeType docs
     */
    public void setOutputMimeType(String outputMimeType) {
        this.outputMimeType = outputMimeType;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * Sets the class of the result type (type from output).
     * <p/>
     * The default result type is Document
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    /**
     * Sets the class name of the result type (type from output)
     * <p/>
     * The default result type is Document
     */
    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }
}
