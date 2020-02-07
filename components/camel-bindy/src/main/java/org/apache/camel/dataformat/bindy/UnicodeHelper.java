package org.apache.camel.dataformat.bindy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.icu.text.BreakIterator;

/**
 * <p>Unterstützung für String-Verarbeitung für Unicode-Strings</p>
 * 
 * <p>Hinweise:</p>
 * <ul>
 *     <li>Die Schnittstelle orientiert sich an der von {@link String}.</li>
 *     <li>Einmal erzeugt ist eine Instanz immutable.</li>
 * </ul>
 */
public class UnicodeHelper 
{
	/**
	 * <p>Definiert wie die Länge eines Strings bzw. dessen Bestandteile &quot;Zeichen&quot; ermittelt wird.</p> 
	 */
	public static enum Method 
	{
		/**
		 * <p>Die Länge entspricht der Anzahl der (Unicode-)Codepoints.</p>
		 */
		CODEPOINTS,
		
		/**
		 * <p>Die Länge entspricht der Anzahl der Grapheme.</p>
		 */
		GRAPHEME;
	}
	
	private static final Logger LOG = LoggerFactory.getLogger(UnicodeHelper.class);
	
	private String input;
	
	private List<Integer> splitted;

	private Method method;
	
	/**
	 * Erzeugt einen LengthHelper
	 * 
	 * @param input
	 * 		String der verarbeitet werden soll.
	 * @param method 
	 * 		Methode wie die Länge ermittelt wird.
	 */
	public UnicodeHelper(final String input,final Method method) {
		this.input = input;
		this.method = method;
		this.splitted = null;
	}

	/**
	 * @return
	 * 		Gibt die verwendete Methode zur Längenermittlung an.
	 */
	public Method getMethod() {
		return method;
	}

	/**
	 * Returns a string that is a substring of this string. The substring begins with the character at 
	 * the specified index and extends to the end of this string.
	 * 
	 * @param beginIndex
	 * 		the beginning index, inclusive.
	 * @return 
	 * 		the specified substring.
	 * @throws java.lang.IndexOutOfBoundsException
	 * 		if beginIndex is negative or larger than the length of this String object.
	 * 
	 * @see String#substring(int)
	 */
	public String substring(final int beginIndex) {
		split();
		
		final int beginChar = splitted.get(beginIndex);
		return input.substring(beginChar);
	}
	
	/**
	 * Returns a string that is a substring of this string. The substring begins at the specified beginIndex 
	 * and extends to the character at index endIndex - 1. Thus the length of the substring is endIndex-beginIndex. 
	 * 
	 * @param beginIndex
	 * 		the beginning index, inclusive.
     * @param endIndex
     * 		the ending index, exclusive.
     * @return
     * 		the specified substring.
	 * @throws java.lang.IndexOutOfBoundsException
	 * 		if the beginIndex is negative, or endIndex is larger than the length of this String object, 
	 * 		or beginIndex is larger than endIndex.
	 * 
	 * @see String#substring(int, int)
	 */
	public String substring(final int beginIndex, final int endIndex) {
		split();
		
		final int beginChar = splitted.get(beginIndex);
		final int endChar = splitted.get(endIndex);
		return input.substring(beginChar,endChar);
	}

	/**
	 * Returns the length of this string. The length is equal to the graphemes in the string.
	 * 
	 * @return
	 * 		Returns the length of this character sequence. 
	 * 
	 * @see String#length()
	 */
	public int length() {
		split();
		
		return splitted.size() - 1;
	}
	
	/**
	 * Return the first index of the string s  
	 * 
	 * @param str
	 * 		The String to found
	 * @return
	 * 		If found the index, else -1 
	 * @throws IllegalArgumentException
	 * 		If argument given to indexOf is not a string conisting fully of valid unicode chars.
	 * 
	 * @see String#indexOf(String)
	 */
	public int indexOf(final String str) {
		split();
		
		final int tempIdx = input.indexOf(str);
		if (tempIdx < 0)
			return tempIdx;
		
		for (int b = 0; b < splitted.size() - 1; b++) {
			if (tempIdx == splitted.get(b)) {
				for (int e = b + 1; e < splitted.size() - 1; e++) {
					if (tempIdx + str.length() == splitted.get(e)) {
						return b;
					}
				}
			}
		}
		
		final String cps = str.codePoints().mapToObj((cp) -> String.format("0x%X",cp)).collect(Collectors.joining(","));
		throw new IllegalArgumentException("Given string (" + cps + ") is not a valid sequence of " + this.method + "s.");
	}
	
	private void split() {
		if (this.splitted != null)
			return;
		
		if (method.equals(Method.CODEPOINTS)) {
			splitCodepoints();
			
		} else /* (method.equals(Method.GRAPHEME)) */ {
			splitGrapheme();
		}
		
		LOG.debug("\"{}\" is splitted into {} ({} {}).",input, splitted, splitted.size() - 1, method);
		if (LOG.isTraceEnabled()) {
			for (int i = 0; i < splitted.size() - 2; i++) {
				LOG.trace("segment [{},{}[=\"{}\".",splitted.get(i),splitted.get(i + 1),input.substring(splitted.get(i),splitted.get(i + 1)));
			}
		}
	}

	private void splitCodepoints() 
	{
		final List<Integer> result = new ArrayList<>();
		
		int i = 0;
		final int len = input.length();
		while (i < len) {
			result.add(i);
			i += (Character.codePointAt(input, i) > 0xffff) ? 2 : 1; 
		}
		result.add(len);
		
		this.splitted = result;
	}

	private void splitGrapheme() {
		final List<Integer> result = new ArrayList<>();

		// 
		// Achtung: Hier wird der BreakIterator der ICU lib (com.ibm.icu.text.BreakIterator; siehe Dependencies) 
		// benutzt, denn der in Java Eingebaute ist zu alt und kann darum mit manchen Zeichen nicht korrekt umgehen.
		//
		final BreakIterator bit = BreakIterator.getCharacterInstance();
		bit.setText(input);
		
		result.add(bit.first());
		for (int end = bit.next(); end != BreakIterator.DONE; end = bit.next()) {
			result.add(end);
		}
		this.splitted = result;
	}
}
