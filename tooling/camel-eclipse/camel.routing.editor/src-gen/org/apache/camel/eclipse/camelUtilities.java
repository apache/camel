package org.apache.camel.eclipse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.TokenStream;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.jface.text.rules.IPartitionTokenScanner;
import org.openarchitectureware.xtext.AbstractLanguageUtilities;
import org.openarchitectureware.type.MetaModel;
import org.openarchitectureware.type.emf.EmfMetaModel;
import org.openarchitectureware.xtext.parser.EcoreModelFactory;
import org.openarchitectureware.xtext.parser.ParseResult;
import org.apache.camel.eclipse.parser.camelLexer;
import org.apache.camel.eclipse.parser.camelParser;
import org.osgi.framework.Bundle;

public class camelUtilities extends AbstractLanguageUtilities {

   public TokenStream getScanner(InputStream reader) throws IOException {
		ANTLRInputStream input = new ANTLRInputStream(reader);
		camelLexer lexer = new camelLexer(input);
		return new CommonTokenStream(lexer);
	}

   public ParseResult internalParse(TokenStream scanner, EcoreModelFactory f)
			throws RecognitionException {
		camelParser p = new camelParser(scanner, f);
		return new ParseResult(p.parse(), p.getErrors());
	}

   public String getCheckFileName() {
      return "org::apache::camel::eclipse::camelChecks";
   }

   public String getFileExtension() {
      return "camel";
   }

	private List<MetaModel> mms = null;
	@Override
	public List<MetaModel> getMetaModels() {
	    if (mms==null) {
			mms = new ArrayList<MetaModel>();
	        EmfMetaModel mm = new EmfMetaModel();
	        mm.setMetaModelFile("org/apache/camel/eclipse/camel.ecore");
			mms.add(mm);
		}
		return mms;
	}

   public String getLabelExtensionsFileName() {
      return "org::apache::camel::eclipse::camelEditorExtensions";
   }
   
   public String getImageExtensionsFileName() {
      return "org::apache::camel::eclipse::camelEditorExtensions";
   }

   public String[] allKeywords() {
      return new String[] { "process","to","from" };
   }
   
   public ClassLoader getClassLoader() {
      return this.getClass().getClassLoader();
   }
   
   public IPartitionTokenScanner getPartitionScanner() {
      return new GeneratedPartitionScanner();
   }
   
   @Override
	public Bundle getPluginBundle() {
		return camelEditorPlugin.getDefault().getBundle();
	}
}
