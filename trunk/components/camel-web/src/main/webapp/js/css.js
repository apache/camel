/**
 * Written by Neil Crosby. 
 * http://www.workingwith.me.uk/
 *
 * Use this wherever you want, but please keep this comment at the top of this file.
 *
 * Copyright (c) 2006 Neil Crosby
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal 
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell 
 * copies of the Software, and to permit persons to whom the Software is 
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, 
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE 
 * SOFTWARE.
 **/
var css = {
	/**
	 * Returns an array containing references to all elements
	 * of a given tag type within a certain node which have a given class
	 *
	 * @param node		the node to start from 
	 *					(e.g. document, 
	 *						  getElementById('whateverStartpointYouWant')
	 *					)
	 * @param searchClass the class we're wanting
	 *					(e.g. 'some_class')
	 * @param tag		 the tag that the found elements are allowed to be
	 *					(e.g. '*', 'div', 'li')
	 **/
	getElementsByClass : function(node, searchClass, tag) {
		var classElements = new Array();
		var els = node.getElementsByTagName(tag);
		var elsLen = els.length;
		var pattern = new RegExp("(^|\\s)"+searchClass+"(\\s|$)");
		
		
		for (var i = 0, j = 0; i < elsLen; i++) {
			if (this.elementHasClass(els[i], searchClass) ) {
				classElements[j] = els[i];
				j++;
			}
		}
		return classElements;
	},


	/**
	 * PRIVATE.  Returns an array containing all the classes applied to this
	 * element.
	 *
	 * Used internally by elementHasClass(), addClassToElement() and 
	 * removeClassFromElement().
	 **/
	privateGetClassArray: function(el) {
		return el.className.split(' '); 
	},

	/**
	 * PRIVATE.  Creates a string from an array of class names which can be used 
	 * by the className function.
	 *
	 * Used internally by addClassToElement().
	 **/
	privateCreateClassString: function(classArray) {
		return classArray.join(' ');
	},

	/**
	 * Returns true if the given element has been assigned the given class.
	 **/
	elementHasClass: function(el, classString) {
		if (!el) {
			return false;
		}
		
		var regex = new RegExp('\\b'+classString+'\\b');
		if (el.className.match(regex)) {
			return true;
		}

		return false;
	},

	/**
	 * Adds classString to the classes assigned to the element with id equal to
	 * idString.
	 **/
	addClassToId: function(idString, classString) {
		this.addClassToElement(document.getElementById(idString), classString);
	},

	/**
	 * Adds classString to the classes assigned to the given element.
	 * If the element already has the class which was to be added, then
	 * it is not added again.
	 **/
	addClassToElement: function(el, classString) {
		var classArray = this.privateGetClassArray(el);

		if (this.elementHasClass(el, classString)) {
			return; // already has element so don't need to add it
		}

		classArray.push(classString);

		el.className = this.privateCreateClassString(classArray);
	},

	/**
	 * Removes the given classString from the list of classes assigned to the
	 * element with id equal to idString
	 **/
	removeClassFromId: function(idString, classString) {
		this.removeClassFromElement(document.getElementById(idString), classString);
	},

	/**
	 * Removes the given classString from the list of classes assigned to the
	 * given element.  If the element has the same class assigned to it twice, 
	 * then only the first instance of that class is removed.
	 **/
	removeClassFromElement: function(el, classString) {
		var classArray = this.privateGetClassArray(el);

		for (x in classArray) {
			if (classString == classArray[x]) {
				classArray[x] = '';
				break;
			}
		}

		el.className = this.privateCreateClassString(classArray);
	}
}