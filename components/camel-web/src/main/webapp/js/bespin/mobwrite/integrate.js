
dojo.provide("bespin.mobwrite.integrate");

// BESPIN

/**
 * Constructor of shared object representing a text field.
 * @param {Node} node A textarea, text or password input
 * @constructor
 */
mobwrite.shareBespinObj = function(node) {
    this._editSession = node;

    var username = this._editSession.username || "[none]";
    var project = this._editSession.project;
    var path = this._editSession.path;
    if (path.indexOf("/") != 0) {
        path = "/" + path;
    }

    var id = username + "/" + project + path;
    // Call our prototype's constructor.
    mobwrite.shareObj.apply(this, [id]);
};

// The textarea shared object's parent is a shareObj.
mobwrite.shareBespinObj.prototype = new mobwrite.shareObj('');

/**
 * Retrieve the user's text.
 * @return {string} Plaintext content.
 */
mobwrite.shareBespinObj.prototype.getClientText = function() {
    // Was:
    // var text = this.element.value;
    var text = this._editSession.editor.model.getDocument();
    text = mobwrite.shareBespinObj.normalizeLinebreaks_(text);
    return text;
};

/**
 * Set the user's text.
 * @param {string} text New text
 */
mobwrite.shareBespinObj.prototype.setClientText = function(text) {
    // Was:
    // this.element.value = text;
    // this.fireChange(this.element);
    this._editSession.editor.model.insertDocument(text);
};

/**
 * Modify the user's plaintext by applying a series of patches against it.
 * @param {Array<patch_obj>} patches Array of Patch objects
 */
mobwrite.shareBespinObj.prototype.patchClientText = function(patches) {
  // Set some constants which tweak the matching behaviour.
  // Tweak the relative importance (0.0 = accuracy, 1.0 = proximity)
  this.dmp.Match_Balance = 0.5;
  // At what point is no match declared (0.0 = perfection, 1.0 = very loose)
  this.dmp.Match_Threshold = 0.6;

  var oldClientText = this.getClientText();
  var result = this.dmp.patch_apply(patches, oldClientText);
  // Set the new text only if there is a change to be made.
  if (oldClientText != result[0]) {
    // var cursor = this.captureCursor_();
    this.setClientText(result[0]);
    // if (cursor) {
    //   this.restoreCursor_(cursor);
    // }
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
 * Ensure that all linebreaks are CR+LF
 * @param {string} text Text with unknown line breaks
 * @return {string} Text with normalized linebreaks
 * @private
 */
mobwrite.shareBespinObj.normalizeLinebreaks_ = function(text) {
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
mobwrite.shareBespinObj.shareHandler = function(node) {
    if (node.editor && node.username && node.project && node.path) {
        return new mobwrite.shareBespinObj(node);
    } else {
        return null;
    }
};

// Register this shareHandler with MobWrite.
mobwrite.shareHandlers.push(mobwrite.shareBespinObj.shareHandler);
