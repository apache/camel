package org.apache.camel.eclipse.parser;

import java.io.IOException;
import java.io.Reader;

import org.antlr.runtime.ANTLRReaderStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

import org.openarchitectureware.xtext.parser.AbstractParserComponent;
import org.openarchitectureware.xtext.parser.EcoreModelFactory;
import org.openarchitectureware.xtext.parser.ParseResult;

public class ParserComponent extends AbstractParserComponent {

   @Override
   protected ParseResult internalParse(Reader reader, EcoreModelFactory factory) throws IOException, RecognitionException {
      ANTLRReaderStream input = new ANTLRReaderStream(reader); 
      camelLexer lexer = new camelLexer(input); 
      CommonTokenStream tokens = new CommonTokenStream(lexer); 
      camelParser p = new camelParser(tokens, factory);
      return new ParseResult(p.parse(),p.getErrors());
   }

}
