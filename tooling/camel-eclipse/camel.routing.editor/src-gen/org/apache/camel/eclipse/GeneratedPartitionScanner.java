package org.apache.camel.eclipse;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.openarchitectureware.xtext.editor.scanning.AbstractPartitionScanner;

public class GeneratedPartitionScanner extends AbstractPartitionScanner {

	@Override
	public List<IPredicateRule> getRules() {
        List<IPredicateRule> rules = new ArrayList<IPredicateRule>();

        rules.add(new MultiLineRule("/*","*/", comment));
        rules.add(new SingleLineRule("//", "", comment));
        rules.add(new MultiLineRule("\"","\"", string));
        rules.add(new MultiLineRule("'","'", string));
        return rules;
	}

	protected IToken getSingleLineCommentToken(String string) {
		return comment;
	}

	protected IToken getStringToken(String start, String end) {
		return string;
	}

}

