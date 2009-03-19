/**
 * MobWrite - Real-time Synchronization and Collaboration Service
 *
 * Copyright 2008 Neil Fraser
 * http://code.google.com/p/google-mobwrite/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @fileoverview This client-side code interfaces with form elements.
 * @author fraser@google.com (Neil Fraser)
 */

dojo.provide("bespin.mobwrite.form");

// FORM


/**
 * Handler to accept forms as elements that can be shared.
 * Share each of the form's elements.
 * @param {*} node Object or ID of object to share
 * @return {Object?} A sharing object or null.
 */
mobwrite.shareHandlerForm = function(form) {
  if (typeof form == 'string') {
    form = document.getElementById(form) || document.forms[form];
  }
  if (form && 'tagName' in form && form.tagName == 'FORM') {
    for (var x = 0, el; el = form.elements[x]; x++) {
      mobwrite.share(el);
    }
  }
  return null;
};


// Register this shareHandler with MobWrite.
mobwrite.shareHandlers.push(mobwrite.shareHandlerForm);


// HIDDEN


/**
 * Constructor of shared object representing a hidden input.
 * @param {Node} node A hidden element
 * @constructor
 */
mobwrite.shareHiddenObj = function(node) {
  // Call our prototype's constructor.
  mobwrite.shareObj.apply(this, [node.id]);
  this.element = node;
};


// The hidden input's shared object's parent is a shareObj.
mobwrite.shareHiddenObj.prototype = new mobwrite.shareObj('');


/**
 * Retrieve the user's content.
 * @return {string} Plaintext content.
 */
mobwrite.shareHiddenObj.prototype.getClientText = function() {
  // Numeric data should use overwrite mode.
  this.mergeChanges = !this.element.value.match(/^\s*-?[\d.]+\s*$/);
  return this.element.value;
};


/**
 * Set the user's content.
 * @param {string} text New content
 */
mobwrite.shareHiddenObj.prototype.setClientText = function(text) {
  this.element.value = text;
};


/**
 * Handler to accept hidden fields as elements that can be shared.
 * If the element is a hidden field, create a new sharing object.
 * @param {*} node Object or ID of object to share
 * @return {Object?} A sharing object or null.
 */
mobwrite.shareHiddenObj.shareHandler = function(node) {
  if (typeof node == 'string') {
    node = document.getElementById(node);
  }
  if (node && 'type' in node && node.type == 'hidden') {
    return new mobwrite.shareHiddenObj(node);
  }
  return null;
};


// Register this shareHandler with MobWrite.
mobwrite.shareHandlers.push(mobwrite.shareHiddenObj.shareHandler);


// CHECKBOX


/**
 * Constructor of shared object representing a checkbox.
 * @param {Node} node A checkbox element
 * @constructor
 */
mobwrite.shareCheckboxObj = function(node) {
  // Call our prototype's constructor.
  mobwrite.shareObj.apply(this, [node.id]);
  this.element = node;
  this.mergeChanges = false;
};


// The checkbox shared object's parent is a shareObj.
mobwrite.shareCheckboxObj.prototype = new mobwrite.shareObj('');


/**
 * Retrieve the user's check.
 * @return {string} Plaintext content.
 */
mobwrite.shareCheckboxObj.prototype.getClientText = function() {
  return this.element.checked ? this.element.value : '';
};


/**
 * Set the user's check.
 * @param {string} text New content
 */
mobwrite.shareCheckboxObj.prototype.setClientText = function(text) {
  // Safari has a blank value if not set, all other browsers have 'on'.
  var value = this.element.value || 'on';
  this.element.checked = (text == value);
  this.fireChange(this.element);
};


/**
 * Handler to accept checkboxen as elements that can be shared.
 * If the element is a checkbox, create a new sharing object.
 * @param {*} node Object or ID of object to share
 * @return {Object?} A sharing object or null.
 */
mobwrite.shareCheckboxObj.shareHandler = function(node) {
  if (typeof node == 'string') {
    node = document.getElementById(node);
  }
  if (node && 'type' in node && node.type == 'checkbox') {
    return new mobwrite.shareCheckboxObj(node);
  }
  return null;
};


// Register this shareHandler with MobWrite.
mobwrite.shareHandlers.push(mobwrite.shareCheckboxObj.shareHandler);


// SELECT OPTION


/**
 * Constructor of shared object representing a select box.
 * @param {Node} node A select box element
 * @constructor
 */
mobwrite.shareSelectObj = function(node) {
  // Call our prototype's constructor.
  mobwrite.shareObj.apply(this, [node.id]);
  this.element = node;
  // If the select box is select-one, use overwrite mode.
  // If it is select-multiple, use text merge mode.
  this.mergeChanges = (node.type == 'select-multiple');
};


// The select box shared object's parent is a shareObj.
mobwrite.shareSelectObj.prototype = new mobwrite.shareObj('');


/**
 * Retrieve the user's selection(s).
 * @return {string} Plaintext content.
 */
mobwrite.shareSelectObj.prototype.getClientText = function() {
  var selected = [];
  for (var x = 0, option; option = this.element.options[x]; x++) {
    if (option.selected) {
      selected.push(option.value);
    }
  }
  return selected.join('\00');
};


/**
 * Set the user's selection(s).
 * @param {string} text New content
 */
mobwrite.shareSelectObj.prototype.setClientText = function(text) {
  text = '\00' + text + '\00';
  for (var x = 0, option; option = this.element.options[x]; x++) {
    option.selected = (text.indexOf('\00' + option.value + '\00') != -1);
  }
  this.fireChange(this.element);
};


/**
 * Handler to accept select boxen as elements that can be shared.
 * If the element is a select box, create a new sharing object.
 * @param {*} node Object or ID of object to share
 * @return {Object?} A sharing object or null.
 */
mobwrite.shareSelectObj.shareHandler = function(node) {
  if (typeof node == 'string') {
    node = document.getElementById(node);
  }
  if (node && 'type' in node && (node.type == 'select-one' || node.type == 'select-multiple')) {
    return new mobwrite.shareSelectObj(node);
  }
  return null;
};


// Register this shareHandler with MobWrite.
mobwrite.shareHandlers.push(mobwrite.shareSelectObj.shareHandler);


// RADIO BUTTON


/**
 * Constructor of shared object representing a radio button.
 * @param {Node} node A radio button element
 * @constructor
 */
mobwrite.shareRadioObj = function(node) {
  // Call our prototype's constructor.
  mobwrite.shareObj.apply(this, [node.id]);
  this.elements = [node];
  this.form = node.form;
  this.name = node.name;
  this.mergeChanges = false;
};


// The radio button shared object's parent is a shareObj.
mobwrite.shareRadioObj.prototype = new mobwrite.shareObj('');


/**
 * Retrieve the user's check.
 * @return {string} Plaintext content.
 */
mobwrite.shareRadioObj.prototype.getClientText = function() {
  // Group of radio buttons
  for (var x = 0; x < this.elements.length; x++) {
    if (this.elements[x].checked) {
      return this.elements[x].value
    }
  }
  // Nothing checked.
  return '';
};


/**
 * Set the user's check.
 * @param {string} text New content
 */
mobwrite.shareRadioObj.prototype.setClientText = function(text) {
  for (var x = 0; x < this.elements.length; x++) {
    this.elements[x].checked = (text == this.elements[x].value);
    this.fireChange(this.elements[x]);
  }
};


/**
 * Handler to accept radio buttons as elements that can be shared.
 * If the element is a radio button, create a new sharing object.
 * @param {*} node Object or ID of object to share
 * @return {Object?} A sharing object or null.
 */
mobwrite.shareRadioObj.shareHandler = function(node) {
  if (typeof node == 'string') {
    node = document.getElementById(node);
  }
  if (node && 'type' in node && node.type == 'radio') {
    // Check to see if this is another element of an existing radio button group.
    for (var id in mobwrite.shared) {
      if (mobwrite.shared[id].form == node.form && mobwrite.shared[id].name == node.name) {
        mobwrite.shared[id].elements.push(node);
        return null;
      }
    }
    // Create new radio button object.
    return new mobwrite.shareRadioObj(node);
  }
  return null;
};


// Register this shareHandler with MobWrite.
mobwrite.shareHandlers.push(mobwrite.shareRadioObj.shareHandler);


// TEXTAREA, TEXT & PASSWORD INPUTS


/**
 * Constructor of shared object representing a text field.
 * @param {Node} node A textarea, text or password input
 * @constructor
 */
mobwrite.shareTextareaObj = function(node) {
  // Call our prototype's constructor.
  mobwrite.shareObj.apply(this, [node.id]);
  this.element = node;
  if (node.type == 'password') {
    // Use overwrite mode for password field, users can't see.
    this.mergeChanges = false;
  }
};


// The textarea shared object's parent is a shareObj.
mobwrite.shareTextareaObj.prototype = new mobwrite.shareObj('');


/**
 * Retrieve the user's text.
 * @return {string} Plaintext content.
 */
mobwrite.shareTextareaObj.prototype.getClientText = function() {
  var text = mobwrite.shareTextareaObj.normalizeLinebreaks_(this.element.value);
  if (this.element.type == 'text') {
    // Numeric data should use overwrite mode.
    this.mergeChanges = !text.match(/^\s*-?[\d.]+\s*$/);
  }
  return text;
};


/**
 * Set the user's text.
 * @param {string} text New text
 */
mobwrite.shareTextareaObj.prototype.setClientText = function(text) {
  this.element.value = text;
  this.fireChange(this.element);
};


/**
 * Modify the user's plaintext by applying a series of patches against it.
 * @param {Array<patch_obj>} patches Array of Patch objects
 */
mobwrite.shareTextareaObj.prototype.patchClientText = function(patches) {
  // Set some constants which tweak the matching behaviour.
  // Tweak the relative importance (0.0 = accuracy, 1.0 = proximity)
  this.dmp.Match_Balance = 0.5;
  // At what point is no match declared (0.0 = perfection, 1.0 = very loose)
  this.dmp.Match_Threshold = 0.6;

  var oldClientText = this.getClientText();
  var result = this.dmp.patch_apply(patches, oldClientText);
  // Set the new text only if there is a change to be made.
  if (oldClientText != result[0]) {
    var cursor = this.captureCursor_();
    this.setClientText(result[0]);
    if (cursor) {
      this.restoreCursor_(cursor);
    }
  }
  if (mobwrite.debug) {
    for (var x = 0; x < result[1].length; x++) {
      if (result[1][x]) {
        console.info('Patch OK.');
      } else {
        console.warn('Patch failed: ' + patches[x]);
      }
   }
  }
};


/**
 * Record information regarding the current cursor.
 * @return {Object?} Context information of the cursor.
 * @private
 */
mobwrite.shareTextareaObj.prototype.captureCursor_ = function() {
  if ('activeElement' in this.element && !this.element.activeElement) {
    // Safari specific code.
    // Restoring a cursor in an unfocused element causes the focus to jump.
    return null;
  }
  var padLength = this.dmp.Match_MaxBits / 2;  // Normally 16.
  var text = this.element.value;
  var cursor = {};
  if ('selectionStart' in this.element) {  // W3
    var selectionStart = this.element.selectionStart;
    var selectionEnd = this.element.selectionEnd;
    cursor.startPrefix = text.substring(selectionStart - padLength, selectionStart);
    cursor.startSuffix = text.substring(selectionStart, selectionStart + padLength);
    cursor.startPercent = selectionStart / text.length;
    cursor.collapsed = (selectionStart == selectionEnd);
    if (!cursor.collapsed) {
      cursor.endPrefix = text.substring(selectionEnd - padLength, selectionEnd);
      cursor.endSuffix = text.substring(selectionEnd, selectionEnd + padLength);
      cursor.endPercent = selectionEnd / text.length;
    }
  } else {  // IE
    // Walk up the tree looking for this textarea's document node.
    var doc = this.element;
    while (doc.parentNode) {
      doc = doc.parentNode;
    }
    if (!doc.selection || !doc.selection.createRange) {
      // Not IE?
      return null;
    }
    var range = doc.selection.createRange();
    if (range.parentElement() != this.element) {
      // Cursor not in this textarea.
      return null;
    }
    var newRange = doc.body.createTextRange();

    cursor.collapsed = (range.text == '');
    newRange.moveToElementText(this.element);
    if (!cursor.collapsed) {
      newRange.setEndPoint('EndToEnd', range);
      cursor.endPrefix = newRange.text;
      cursor.endPercent = cursor.endPrefix.length / text.length;
      cursor.endPrefix = cursor.endPrefix.substring(cursor.endPrefix.length - padLength);
    }
    newRange.setEndPoint('EndToStart', range);
    cursor.startPrefix = newRange.text;
    cursor.startPercent = cursor.startPrefix.length / text.length;
    cursor.startPrefix = cursor.startPrefix.substring(cursor.startPrefix.length - padLength);

    newRange.moveToElementText(this.element);
    newRange.setEndPoint('StartToStart', range);
    cursor.startSuffix = newRange.text.substring(0, padLength);
    if (!cursor.collapsed) {
      newRange.setEndPoint('StartToEnd', range);
      cursor.endSuffix = newRange.text.substring(0, padLength);
    }
  }

  // Record scrollbar locations
  if ('scrollTop' in this.element) {
    cursor.scrollTop = this.element.scrollTop / this.element.scrollHeight;
    cursor.scrollLeft = this.element.scrollLeft / this.element.scrollWidth;
  }
  
  // alert(cursor.startPrefix + '|' + cursor.startSuffix + ' ' +
  //     cursor.startPercent + '\n' + cursor.endPrefix + '|' +
  //     cursor.endSuffix + ' ' + cursor.endPercent + '\n' +
  //     cursor.scrollTop + ' x ' + cursor.scrollLeft);
  return cursor;
};


/**
 * Attempt to restore the cursor's location.
 * @param {Object} cursor Context information of the cursor.
 * @private
 */
mobwrite.shareTextareaObj.prototype.restoreCursor_ = function(cursor) {
  // Set some constants which tweak the matching behaviour.
  // Tweak the relative importance (0.0 = accuracy, 1.0 = proximity)
  this.dmp.Match_Balance = 0.4;
  // At what point is no match declared (0.0 = perfection, 1.0 = very loose)
  this.dmp.Match_Threshold = 0.9;

  var padLength = this.dmp.Match_MaxBits / 2;  // Normally 16.
  var newText = this.element.value;

  // Find the start of the selection in the new text.
  var pattern1 = cursor.startPrefix + cursor.startSuffix;
  var cursorStartPoint = this.dmp.match_main(newText, pattern1,
      Math.round(Math.max(0, Math.min(newText.length,
          cursor.startPercent * newText.length - padLength))));
  if (cursorStartPoint !== null) {
    var pattern2 = newText.substring(cursorStartPoint,
                                     cursorStartPoint + pattern1.length);
    //alert(pattern1 + '\nvs\n' + pattern2);
    // Run a diff to get a framework of equivalent indicies.
    var diff = this.dmp.diff_main(pattern1, pattern2, false);
    cursorStartPoint += this.dmp.diff_xIndex(diff, cursor.startPrefix.length);
  }

  var cursorEndPoint = null;
  if (!cursor.collapsed) {
    // Find the end of the selection in the new text.
    pattern1 = cursor.endPrefix + cursor.endSuffix;
    cursorEndPoint = this.dmp.match_main(newText, pattern1,
        Math.round(Math.max(0, Math.min(newText.length,
            cursor.endPercent * newText.length - padLength))));
    if (cursorEndPoint !== null) {
      var pattern2 = newText.substring(cursorEndPoint,
                                       cursorEndPoint + pattern1.length);
      //alert(pattern1 + '\nvs\n' + pattern2);
      // Run a diff to get a framework of equivalent indicies.
      var diff = this.dmp.diff_main(pattern1, pattern2, false);
      cursorEndPoint += this.dmp.diff_xIndex(diff, cursor.endPrefix.length);
    }
  }
  
  // Deal with loose ends
  if (cursorStartPoint === null && cursorEndPoint !== null) {
    // Lost the start point of the selection, but we have the end point.
    // Collapse to end point.
    cursorStartPoint = cursorEndPoint;
  } else if (cursorStartPoint === null && cursorEndPoint === null) {
    // Lost both start and end points.
    // Jump to the aproximate percentage point of start.
    cursorStartPoint = Math.round(cursor.startPercent * newText.length);
  }
  if (cursorEndPoint == null) {
    // End not known, collapse to start.
    cursorEndPoint = cursorStartPoint;
  }
  
  // Restore selection.
  if ('selectionStart' in this.element) {  // W3
    this.element.selectionStart = cursorStartPoint;
    this.element.selectionEnd = cursorEndPoint;
  } else {  // IE
    // Walk up the tree looking for this textarea's document node.
    var doc = this.element;
    while (doc.parentNode) {
      doc = doc.parentNode;
    }
    if (!doc.selection || !doc.selection.createRange) {
      // Not IE?
      return;
    }
    // IE's TextRange.move functions treat '\r\n' as one character.
    var snippet = this.element.value.substring(0, cursorStartPoint);
    var ieStartPoint = snippet.replace(/\r\n/g, '\n').length;

    var newRange = doc.body.createTextRange();
    newRange.moveToElementText(this.element);
    newRange.collapse(true);
    newRange.moveStart('character', ieStartPoint);
    if (!cursor.collapsed) {
      snippet = this.element.value.substring(cursorStartPoint, cursorEndPoint);
      var ieMidLength = snippet.replace(/\r\n/g, '\n').length;
      newRange.moveEnd('character', ieMidLength);
    }
    newRange.select();
  }

  // Restore scrollbar locations
  if ('scrollTop' in cursor) {
    this.element.scrollTop = cursor.scrollTop * this.element.scrollHeight;
    this.element.scrollLeft = cursor.scrollLeft * this.element.scrollWidth;
  }
};


/**
 * Ensure that all linebreaks are CR+LF
 * @param {string} text Text with unknown line breaks
 * @return {string} Text with normalized linebreaks
 * @private
 */
mobwrite.shareTextareaObj.normalizeLinebreaks_ = function(text) {
  var oldtext = '';
  if (text != '') {
    // First, fix the first/last chars.
    if (text.charAt(0) == '\n') {
      text = '\r' + text;
    }
    if (text.charAt(text.length - 1) == '\r') {
      text = text + '\n';
    }
  }
  // Second, fix the middle chars.
  while (oldtext != text) {
    oldtext = text;
    text = text.replace(/([^\r])\n/g, '$1\r\n');
    text = text.replace(/\r([^\n])/g, '\r\n$1');
  }
  return text;
};


/**
 * Handler to accept text fields as elements that can be shared.
 * If the element is a textarea, text or password input, create a new
 * sharing object.
 * @param {*} node Object or ID of object to share
 * @return {Object?} A sharing object or null.
 */
mobwrite.shareTextareaObj.shareHandler = function(node) {
  if (typeof node == 'string') {
    node = document.getElementById(node);
  }
  if (node && 'value' in node && 'type' in node && (node.type == 'textarea' ||
      node.type == 'text' || node.type == 'password')) {
    if (mobwrite.UA_webkit) {
      // Safari needs to track which text element has the focus.
      node.addEventListener('focus', function() {this.activeElement = true},
          false);
      node.addEventListener('blur', function() {this.activeElement = false},
          false);
      node.activeElement = false;
    }
    return new mobwrite.shareTextareaObj(node);
  }
  return null;
};


// Register this shareHandler with MobWrite.
mobwrite.shareHandlers.push(mobwrite.shareTextareaObj.shareHandler);
