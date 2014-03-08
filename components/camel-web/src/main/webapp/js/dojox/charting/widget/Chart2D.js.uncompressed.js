/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/

/*
	This is a compiled version of Dojo, built for deployment and not for
	development. To get an editable version, please visit:

		http://dojotoolkit.org

	for documentation and information on getting the source.
*/

if(!dojo._hasResource["dojox.gfx.matrix"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.gfx.matrix"] = true;
dojo.provide("dojox.gfx.matrix");

(function(){
	var m = dojox.gfx.matrix;

	// candidates for dojox.math:
	m._degToRad = function(degree){ return Math.PI * degree / 180; };
	m._radToDeg = function(radian){ return radian / Math.PI * 180; };

	m.Matrix2D = function(arg){
		// summary: a 2D matrix object
		// description: Normalizes a 2D matrix-like object. If arrays is passed,
		//		all objects of the array are normalized and multiplied sequentially.
		// arg: Object
		//		a 2D matrix-like object, a number, or an array of such objects
		if(arg){
			if(typeof arg == "number"){
				this.xx = this.yy = arg;
			}else if(arg instanceof Array){
				if(arg.length > 0){
					var matrix = m.normalize(arg[0]);
					// combine matrices
					for(var i = 1; i < arg.length; ++i){
						var l = matrix, r = dojox.gfx.matrix.normalize(arg[i]);
						matrix = new m.Matrix2D();
						matrix.xx = l.xx * r.xx + l.xy * r.yx;
						matrix.xy = l.xx * r.xy + l.xy * r.yy;
						matrix.yx = l.yx * r.xx + l.yy * r.yx;
						matrix.yy = l.yx * r.xy + l.yy * r.yy;
						matrix.dx = l.xx * r.dx + l.xy * r.dy + l.dx;
						matrix.dy = l.yx * r.dx + l.yy * r.dy + l.dy;
					}
					dojo.mixin(this, matrix);
				}
			}else{
				dojo.mixin(this, arg);
			}
		}
	};

	// the default (identity) matrix, which is used to fill in missing values
	dojo.extend(m.Matrix2D, {xx: 1, xy: 0, yx: 0, yy: 1, dx: 0, dy: 0});

	dojo.mixin(m, {
		// summary: class constants, and methods of dojox.gfx.matrix

		// matrix constants

		// identity: dojox.gfx.matrix.Matrix2D
		//		an identity matrix constant: identity * (x, y) == (x, y)
		identity: new m.Matrix2D(),

		// flipX: dojox.gfx.matrix.Matrix2D
		//		a matrix, which reflects points at x = 0 line: flipX * (x, y) == (-x, y)
		flipX:    new m.Matrix2D({xx: -1}),

		// flipY: dojox.gfx.matrix.Matrix2D
		//		a matrix, which reflects points at y = 0 line: flipY * (x, y) == (x, -y)
		flipY:    new m.Matrix2D({yy: -1}),

		// flipXY: dojox.gfx.matrix.Matrix2D
		//		a matrix, which reflects points at the origin of coordinates: flipXY * (x, y) == (-x, -y)
		flipXY:   new m.Matrix2D({xx: -1, yy: -1}),

		// matrix creators

		translate: function(a, b){
			// summary: forms a translation matrix
			// description: The resulting matrix is used to translate (move) points by specified offsets.
			// a: Number: an x coordinate value
			// b: Number: a y coordinate value
			if(arguments.length > 1){
				return new m.Matrix2D({dx: a, dy: b}); // dojox.gfx.matrix.Matrix2D
			}
			// branch
			// a: dojox.gfx.Point: a point-like object, which specifies offsets for both dimensions
			// b: null
			return new m.Matrix2D({dx: a.x, dy: a.y}); // dojox.gfx.matrix.Matrix2D
		},
		scale: function(a, b){
			// summary: forms a scaling matrix
			// description: The resulting matrix is used to scale (magnify) points by specified offsets.
			// a: Number: a scaling factor used for the x coordinate
			// b: Number: a scaling factor used for the y coordinate
			if(arguments.length > 1){
				return new m.Matrix2D({xx: a, yy: b}); // dojox.gfx.matrix.Matrix2D
			}
			if(typeof a == "number"){
				// branch
				// a: Number: a uniform scaling factor used for the both coordinates
				// b: null
				return new m.Matrix2D({xx: a, yy: a}); // dojox.gfx.matrix.Matrix2D
			}
			// branch
			// a: dojox.gfx.Point: a point-like object, which specifies scale factors for both dimensions
			// b: null
			return new m.Matrix2D({xx: a.x, yy: a.y}); // dojox.gfx.matrix.Matrix2D
		},
		rotate: function(angle){
			// summary: forms a rotating matrix
			// description: The resulting matrix is used to rotate points
			//		around the origin of coordinates (0, 0) by specified angle.
			// angle: Number: an angle of rotation in radians (>0 for CW)
			var c = Math.cos(angle);
			var s = Math.sin(angle);
			return new m.Matrix2D({xx: c, xy: -s, yx: s, yy: c}); // dojox.gfx.matrix.Matrix2D
		},
		rotateg: function(degree){
			// summary: forms a rotating matrix
			// description: The resulting matrix is used to rotate points
			//		around the origin of coordinates (0, 0) by specified degree.
			//		See dojox.gfx.matrix.rotate() for comparison.
			// degree: Number: an angle of rotation in degrees (>0 for CW)
			return m.rotate(m._degToRad(degree)); // dojox.gfx.matrix.Matrix2D
		},
		skewX: function(angle) {
			// summary: forms an x skewing matrix
			// description: The resulting matrix is used to skew points in the x dimension
			//		around the origin of coordinates (0, 0) by specified angle.
			// angle: Number: an skewing angle in radians
			return new m.Matrix2D({xy: Math.tan(angle)}); // dojox.gfx.matrix.Matrix2D
		},
		skewXg: function(degree){
			// summary: forms an x skewing matrix
			// description: The resulting matrix is used to skew points in the x dimension
			//		around the origin of coordinates (0, 0) by specified degree.
			//		See dojox.gfx.matrix.skewX() for comparison.
			// degree: Number: an skewing angle in degrees
			return m.skewX(m._degToRad(degree)); // dojox.gfx.matrix.Matrix2D
		},
		skewY: function(angle){
			// summary: forms a y skewing matrix
			// description: The resulting matrix is used to skew points in the y dimension
			//		around the origin of coordinates (0, 0) by specified angle.
			// angle: Number: an skewing angle in radians
			return new m.Matrix2D({yx: Math.tan(angle)}); // dojox.gfx.matrix.Matrix2D
		},
		skewYg: function(degree){
			// summary: forms a y skewing matrix
			// description: The resulting matrix is used to skew points in the y dimension
			//		around the origin of coordinates (0, 0) by specified degree.
			//		See dojox.gfx.matrix.skewY() for comparison.
			// degree: Number: an skewing angle in degrees
			return m.skewY(m._degToRad(degree)); // dojox.gfx.matrix.Matrix2D
		},
		reflect: function(a, b){
			// summary: forms a reflection matrix
			// description: The resulting matrix is used to reflect points around a vector,
			//		which goes through the origin.
			// a: dojox.gfx.Point: a point-like object, which specifies a vector of reflection
			// b: null
			if(arguments.length == 1){
				b = a.y;
				a = a.x;
			}
			// branch
			// a: Number: an x coordinate value
			// b: Number: a y coordinate value

			// make a unit vector
			var a2 = a * a, b2 = b * b, n2 = a2 + b2, xy = 2 * a * b / n2;
			return new m.Matrix2D({xx: 2 * a2 / n2 - 1, xy: xy, yx: xy, yy: 2 * b2 / n2 - 1}); // dojox.gfx.matrix.Matrix2D
		},
		project: function(a, b){
			// summary: forms an orthogonal projection matrix
			// description: The resulting matrix is used to project points orthogonally on a vector,
			//		which goes through the origin.
			// a: dojox.gfx.Point: a point-like object, which specifies a vector of projection
			// b: null
			if(arguments.length == 1){
				b = a.y;
				a = a.x;
			}
			// branch
			// a: Number: an x coordinate value
			// b: Number: a y coordinate value

			// make a unit vector
			var a2 = a * a, b2 = b * b, n2 = a2 + b2, xy = a * b / n2;
			return new m.Matrix2D({xx: a2 / n2, xy: xy, yx: xy, yy: b2 / n2}); // dojox.gfx.matrix.Matrix2D
		},

		// ensure matrix 2D conformance
		normalize: function(matrix){
			// summary: converts an object to a matrix, if necessary
			// description: Converts any 2D matrix-like object or an array of
			//		such objects to a valid dojox.gfx.matrix.Matrix2D object.
			// matrix: Object: an object, which is converted to a matrix, if necessary
			return (matrix instanceof m.Matrix2D) ? matrix : new m.Matrix2D(matrix); // dojox.gfx.matrix.Matrix2D
		},

		// common operations

		clone: function(matrix){
			// summary: creates a copy of a 2D matrix
			// matrix: dojox.gfx.matrix.Matrix2D: a 2D matrix-like object to be cloned
			var obj = new m.Matrix2D();
			for(var i in matrix){
				if(typeof(matrix[i]) == "number" && typeof(obj[i]) == "number" && obj[i] != matrix[i]) obj[i] = matrix[i];
			}
			return obj; // dojox.gfx.matrix.Matrix2D
		},
		invert: function(matrix){
			// summary: inverts a 2D matrix
			// matrix: dojox.gfx.matrix.Matrix2D: a 2D matrix-like object to be inverted
			var M = m.normalize(matrix),
				D = M.xx * M.yy - M.xy * M.yx,
				M = new m.Matrix2D({
					xx: M.yy/D, xy: -M.xy/D,
					yx: -M.yx/D, yy: M.xx/D,
					dx: (M.xy * M.dy - M.yy * M.dx) / D,
					dy: (M.yx * M.dx - M.xx * M.dy) / D
				});
			return M; // dojox.gfx.matrix.Matrix2D
		},
		_multiplyPoint: function(matrix, x, y){
			// summary: applies a matrix to a point
			// matrix: dojox.gfx.matrix.Matrix2D: a 2D matrix object to be applied
			// x: Number: an x coordinate of a point
			// y: Number: a y coordinate of a point
			return {x: matrix.xx * x + matrix.xy * y + matrix.dx, y: matrix.yx * x + matrix.yy * y + matrix.dy}; // dojox.gfx.Point
		},
		multiplyPoint: function(matrix, /* Number||Point */ a, /* Number, optional */ b){
			// summary: applies a matrix to a point
			// matrix: dojox.gfx.matrix.Matrix2D: a 2D matrix object to be applied
			// a: Number: an x coordinate of a point
			// b: Number: a y coordinate of a point
			var M = m.normalize(matrix);
			if(typeof a == "number" && typeof b == "number"){
				return m._multiplyPoint(M, a, b); // dojox.gfx.Point
			}
			// branch
			// matrix: dojox.gfx.matrix.Matrix2D: a 2D matrix object to be applied
			// a: dojox.gfx.Point: a point
			// b: null
			return m._multiplyPoint(M, a.x, a.y); // dojox.gfx.Point
		},
		multiply: function(matrix){
			// summary: combines matrices by multiplying them sequentially in the given order
			// matrix: dojox.gfx.matrix.Matrix2D...: a 2D matrix-like object,
			//		all subsequent arguments are matrix-like objects too
			var M = m.normalize(matrix);
			// combine matrices
			for(var i = 1; i < arguments.length; ++i){
				var l = M, r = m.normalize(arguments[i]);
				M = new m.Matrix2D();
				M.xx = l.xx * r.xx + l.xy * r.yx;
				M.xy = l.xx * r.xy + l.xy * r.yy;
				M.yx = l.yx * r.xx + l.yy * r.yx;
				M.yy = l.yx * r.xy + l.yy * r.yy;
				M.dx = l.xx * r.dx + l.xy * r.dy + l.dx;
				M.dy = l.yx * r.dx + l.yy * r.dy + l.dy;
			}
			return M; // dojox.gfx.matrix.Matrix2D
		},

		// high level operations

		_sandwich: function(matrix, x, y){
			// summary: applies a matrix at a centrtal point
			// matrix: dojox.gfx.matrix.Matrix2D: a 2D matrix-like object, which is applied at a central point
			// x: Number: an x component of the central point
			// y: Number: a y component of the central point
			return m.multiply(m.translate(x, y), matrix, m.translate(-x, -y)); // dojox.gfx.matrix.Matrix2D
		},
		scaleAt: function(a, b, c, d){
			// summary: scales a picture using a specified point as a center of scaling
			// description: Compare with dojox.gfx.matrix.scale().
			// a: Number: a scaling factor used for the x coordinate
			// b: Number: a scaling factor used for the y coordinate
			// c: Number: an x component of a central point
			// d: Number: a y component of a central point

			// accepts several signatures:
			//	1) uniform scale factor, Point
			//	2) uniform scale factor, x, y
			//	3) x scale, y scale, Point
			//	4) x scale, y scale, x, y

			switch(arguments.length){
				case 4:
					// a and b are scale factor components, c and d are components of a point
					return m._sandwich(m.scale(a, b), c, d); // dojox.gfx.matrix.Matrix2D
				case 3:
					if(typeof c == "number"){
						// branch
						// a: Number: a uniform scaling factor used for both coordinates
						// b: Number: an x component of a central point
						// c: Number: a y component of a central point
						// d: null
						return m._sandwich(m.scale(a), b, c); // dojox.gfx.matrix.Matrix2D
					}
					// branch
					// a: Number: a scaling factor used for the x coordinate
					// b: Number: a scaling factor used for the y coordinate
					// c: dojox.gfx.Point: a central point
					// d: null
					return m._sandwich(m.scale(a, b), c.x, c.y); // dojox.gfx.matrix.Matrix2D
			}
			// branch
			// a: Number: a uniform scaling factor used for both coordinates
			// b: dojox.gfx.Point: a central point
			// c: null
			// d: null
			return m._sandwich(m.scale(a), b.x, b.y); // dojox.gfx.matrix.Matrix2D
		},
		rotateAt: function(angle, a, b){
			// summary: rotates a picture using a specified point as a center of rotation
			// description: Compare with dojox.gfx.matrix.rotate().
			// angle: Number: an angle of rotation in radians (>0 for CW)
			// a: Number: an x component of a central point
			// b: Number: a y component of a central point

			// accepts several signatures:
			//	1) rotation angle in radians, Point
			//	2) rotation angle in radians, x, y

			if(arguments.length > 2){
				return m._sandwich(m.rotate(angle), a, b); // dojox.gfx.matrix.Matrix2D
			}

			// branch
			// angle: Number: an angle of rotation in radians (>0 for CCW)
			// a: dojox.gfx.Point: a central point
			// b: null
			return m._sandwich(m.rotate(angle), a.x, a.y); // dojox.gfx.matrix.Matrix2D
		},
		rotategAt: function(degree, a, b){
			// summary: rotates a picture using a specified point as a center of rotation
			// description: Compare with dojox.gfx.matrix.rotateg().
			// degree: Number: an angle of rotation in degrees (>0 for CW)
			// a: Number: an x component of a central point
			// b: Number: a y component of a central point

			// accepts several signatures:
			//	1) rotation angle in degrees, Point
			//	2) rotation angle in degrees, x, y

			if(arguments.length > 2){
				return m._sandwich(m.rotateg(degree), a, b); // dojox.gfx.matrix.Matrix2D
			}

			// branch
			// degree: Number: an angle of rotation in degrees (>0 for CCW)
			// a: dojox.gfx.Point: a central point
			// b: null
			return m._sandwich(m.rotateg(degree), a.x, a.y); // dojox.gfx.matrix.Matrix2D
		},
		skewXAt: function(angle, a, b){
			// summary: skews a picture along the x axis using a specified point as a center of skewing
			// description: Compare with dojox.gfx.matrix.skewX().
			// angle: Number: an skewing angle in radians
			// a: Number: an x component of a central point
			// b: Number: a y component of a central point

			// accepts several signatures:
			//	1) skew angle in radians, Point
			//	2) skew angle in radians, x, y

			if(arguments.length > 2){
				return m._sandwich(m.skewX(angle), a, b); // dojox.gfx.matrix.Matrix2D
			}

			// branch
			// angle: Number: an skewing angle in radians
			// a: dojox.gfx.Point: a central point
			// b: null
			return m._sandwich(m.skewX(angle), a.x, a.y); // dojox.gfx.matrix.Matrix2D
		},
		skewXgAt: function(degree, a, b){
			// summary: skews a picture along the x axis using a specified point as a center of skewing
			// description: Compare with dojox.gfx.matrix.skewXg().
			// degree: Number: an skewing angle in degrees
			// a: Number: an x component of a central point
			// b: Number: a y component of a central point

			// accepts several signatures:
			//	1) skew angle in degrees, Point
			//	2) skew angle in degrees, x, y

			if(arguments.length > 2){
				return m._sandwich(m.skewXg(degree), a, b); // dojox.gfx.matrix.Matrix2D
			}

			// branch
			// degree: Number: an skewing angle in degrees
			// a: dojox.gfx.Point: a central point
			// b: null
			return m._sandwich(m.skewXg(degree), a.x, a.y); // dojox.gfx.matrix.Matrix2D
		},
		skewYAt: function(angle, a, b){
			// summary: skews a picture along the y axis using a specified point as a center of skewing
			// description: Compare with dojox.gfx.matrix.skewY().
			// angle: Number: an skewing angle in radians
			// a: Number: an x component of a central point
			// b: Number: a y component of a central point

			// accepts several signatures:
			//	1) skew angle in radians, Point
			//	2) skew angle in radians, x, y

			if(arguments.length > 2){
				return m._sandwich(m.skewY(angle), a, b); // dojox.gfx.matrix.Matrix2D
			}

			// branch
			// angle: Number: an skewing angle in radians
			// a: dojox.gfx.Point: a central point
			// b: null
			return m._sandwich(m.skewY(angle), a.x, a.y); // dojox.gfx.matrix.Matrix2D
		},
		skewYgAt: function(/* Number */ degree, /* Number||Point */ a, /* Number, optional */ b){
			// summary: skews a picture along the y axis using a specified point as a center of skewing
			// description: Compare with dojox.gfx.matrix.skewYg().
			// degree: Number: an skewing angle in degrees
			// a: Number: an x component of a central point
			// b: Number: a y component of a central point

			// accepts several signatures:
			//	1) skew angle in degrees, Point
			//	2) skew angle in degrees, x, y

			if(arguments.length > 2){
				return m._sandwich(m.skewYg(degree), a, b); // dojox.gfx.matrix.Matrix2D
			}

			// branch
			// degree: Number: an skewing angle in degrees
			// a: dojox.gfx.Point: a central point
			// b: null
			return m._sandwich(m.skewYg(degree), a.x, a.y); // dojox.gfx.matrix.Matrix2D
		}

		//TODO: rect-to-rect mapping, scale-to-fit (isotropic and anisotropic versions)

	});
})();

// propagate Matrix2D up
dojox.gfx.Matrix2D = dojox.gfx.matrix.Matrix2D;

}

if(!dojo._hasResource["dojox.gfx._base"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.gfx._base"] = true;
dojo.provide("dojox.gfx._base");

(function(){
	var g = dojox.gfx, b = g._base;

	// candidates for dojox.style (work on VML and SVG nodes)
	g._hasClass = function(/*DomNode*/node, /*String*/classStr){
		//	summary:
		//		Returns whether or not the specified classes are a portion of the
		//		class list currently applied to the node.
		// return (new RegExp('(^|\\s+)'+classStr+'(\\s+|$)')).test(node.className)	// Boolean
		var cls = node.getAttribute("className");
		return cls && (" " + cls + " ").indexOf(" " + classStr + " ") >= 0;  // Boolean
	}
	g._addClass = function(/*DomNode*/node, /*String*/classStr){
		//	summary:
		//		Adds the specified classes to the end of the class list on the
		//		passed node.
		var cls = node.getAttribute("className") || "";
		if(!cls || (" " + cls + " ").indexOf(" " + classStr + " ") < 0){
			node.setAttribute("className", cls + (cls ? " " : "") + classStr);
		}
	}
	g._removeClass = function(/*DomNode*/node, /*String*/classStr){
		//	summary: Removes classes from node.
		var cls = node.getAttribute("className");
		if(cls){
			node.setAttribute("className", cls.replace(new RegExp('(^|\\s+)' + classStr + '(\\s+|$)'), "$1$2"));
		}
	}

	// candidate for dojox.html.metrics (dynamic font resize handler is not implemented here)

	//	derived from Morris John's emResized measurer
	b._getFontMeasurements = function(){
		//	summary
		//	Returns an object that has pixel equivilents of standard font size values.
		var heights = {
			'1em':0, '1ex':0, '100%':0, '12pt':0, '16px':0, 'xx-small':0, 'x-small':0,
			'small':0, 'medium':0, 'large':0, 'x-large':0, 'xx-large':0
		};

		if(dojo.isIE){
			//	we do a font-size fix if and only if one isn't applied already.
			//	NOTE: If someone set the fontSize on the HTML Element, this will kill it.
			dojo.doc.documentElement.style.fontSize="100%";
		}

		//	set up the measuring node.
		var div=dojo.doc.createElement("div");
		div.style.position="absolute";
		div.style.left="-100px";
		div.style.top="0";
		div.style.width="30px";
		div.style.height="1000em";
		div.style.border="0";
		div.style.margin="0";
		div.style.padding="0";
		div.style.outline="0";
		div.style.lineHeight="1";
		div.style.overflow="hidden";
		dojo.body().appendChild(div);

		//	do the measurements.
		for(var p in heights){
			div.style.fontSize = p;
			heights[p] = Math.round(div.offsetHeight * 12/16) * 16/12 / 1000;
		}

		dojo.body().removeChild(div);
		div = null;
		return heights; 	//	object
	};

	var fontMeasurements = null;

	b._getCachedFontMeasurements = function(recalculate){
		if(recalculate || !fontMeasurements){
			fontMeasurements = b._getFontMeasurements();
		}
		return fontMeasurements;
	};

	// candidate for dojox.html.metrics

	var measuringNode = null, empty = {};
	b._getTextBox = function(/* String */ text, /* Object */ style, /* String? */ className){
		var m;
		if(!measuringNode){
			m = measuringNode = dojo.doc.createElement("div");
			m.style.position = "absolute";
			m.style.left = "-10000px";
			m.style.top = "0";
			dojo.body().appendChild(m);
		}else{
			m = measuringNode;
		}
		// reset styles
		m.className = "";
		m.style.border = "0";
		m.style.margin = "0";
		m.style.padding = "0";
		m.style.outline = "0";
		// set new style
		if(arguments.length > 1 && style){
			for(var i in style){
				if(i in empty){ continue; }
				m.style[i] = style[i];
			}
		}
		// set classes
		if(arguments.length > 2 && className){
			m.className = className;
		}
		// take a measure
		m.innerHTML = text;
		return dojo.marginBox(m);
	};

	// candidate for dojo.dom

	var uniqueId = 0;
	b._getUniqueId = function(){
		// summary: returns a unique string for use with any DOM element
		var id;
		do{
			id = dojo._scopeName + "Unique" + (++uniqueId);
		}while(dojo.byId(id));
		return id;
	};
})();

dojo.mixin(dojox.gfx, {
	// summary: defines constants, prototypes, and utility functions

	// default shapes, which are used to fill in missing parameters
	defaultPath:     {type: "path",     path: ""},
	defaultPolyline: {type: "polyline", points: []},
	defaultRect:     {type: "rect",     x: 0, y: 0, width: 100, height: 100, r: 0},
	defaultEllipse:  {type: "ellipse",  cx: 0, cy: 0, rx: 200, ry: 100},
	defaultCircle:   {type: "circle",   cx: 0, cy: 0, r: 100},
	defaultLine:     {type: "line",     x1: 0, y1: 0, x2: 100, y2: 100},
	defaultImage:    {type: "image",    x: 0, y: 0, width: 0, height: 0, src: ""},
	defaultText:     {type: "text",     x: 0, y: 0, text: "",
		align: "start", decoration: "none", rotated: false, kerning: true },
	defaultTextPath: {type: "textpath", text: "",
		align: "start", decoration: "none", rotated: false, kerning: true },

	// default geometric attributes
	defaultStroke: {type: "stroke", color: "black", style: "solid", width: 1, cap: "butt", join: 4},
	defaultLinearGradient: {type: "linear", x1: 0, y1: 0, x2: 100, y2: 100,
		colors: [{offset: 0, color: "black"}, {offset: 1, color: "white"}]},
	defaultRadialGradient: {type: "radial", cx: 0, cy: 0, r: 100,
		colors: [{offset: 0, color: "black"}, {offset: 1, color: "white"}]},
	defaultPattern: {type: "pattern", x: 0, y: 0, width: 0, height: 0, src: ""},
	defaultFont: {type: "font", style: "normal", variant: "normal", weight: "normal",
		size: "10pt", family: "serif"},

	normalizeColor: function(/*Color*/ color){
		// summary: converts any legal color representation to normalized dojo.Color object
		return (color instanceof dojo.Color) ? color : new dojo.Color(color); // dojo.Color
	},
	normalizeParameters: function(existed, update){
		// summary: updates an existing object with properties from an "update" object
		// existed: Object: the "target" object to be updated
		// update:  Object: the "update" object, whose properties will be used to update the existed object
		if(update){
			var empty = {};
			for(var x in existed){
				if(x in update && !(x in empty)){
					existed[x] = update[x];
				}
			}
		}
		return existed;	// Object
	},
	makeParameters: function(defaults, update){
		// summary: copies the original object, and all copied properties from the "update" object
		// defaults: Object: the object to be cloned before updating
		// update:   Object: the object, which properties are to be cloned during updating
		if(!update) return dojo.clone(defaults);
		var result = {};
		for(var i in defaults){
			if(!(i in result)){
				result[i] = dojo.clone((i in update) ? update[i] : defaults[i]);
			}
		}
		return result; // Object
	},
	formatNumber: function(x, addSpace){
		// summary: converts a number to a string using a fixed notation
		// x:			Number:		number to be converted
		// addSpace:	Boolean?:	if it is true, add a space before a positive number
		var val = x.toString();
		if(val.indexOf("e") >= 0){
			val = x.toFixed(4);
		}else{
			var point = val.indexOf(".");
			if(point >= 0 && val.length - point > 5){
				val = x.toFixed(4);
			}
		}
		if(x < 0){
			return val; // String
		}
		return addSpace ? " " + val : val; // String
	},
	// font operations
	makeFontString: function(font){
		// summary: converts a font object to a CSS font string
		// font:	Object:	font object (see dojox.gfx.defaultFont)
		return font.style + " " + font.variant + " " + font.weight + " " + font.size + " " + font.family; // Object
	},
	splitFontString: function(str){
		// summary: converts a CSS font string to a font object
		// str:		String:	a CSS font string
		var font = dojo.clone(dojox.gfx.defaultFont);
		var t = str.split(/\s+/);
		do{
			if(t.length < 5){ break; }
			font.style  = t[0];
			font.varian = t[1];
			font.weight = t[2];
			var i = t[3].indexOf("/");
			font.size = i < 0 ? t[3] : t[3].substring(0, i);
			var j = 4;
			if(i < 0){
				if(t[4] == "/"){
					j = 6;
					break;
				}
				if(t[4].substr(0, 1) == "/"){
					j = 5;
					break;
				}
			}
			if(j + 3 > t.length){ break; }
			font.size = t[j];
			font.family = t[j + 1];
		}while(false);
		return font;	// Object
	},
	// length operations
	cm_in_pt: 72 / 2.54,	// Number: points per centimeter
	mm_in_pt: 7.2 / 2.54,	// Number: points per millimeter
	px_in_pt: function(){
		// summary: returns a number of pixels per point
		return dojox.gfx._base._getCachedFontMeasurements()["12pt"] / 12;	// Number
	},
	pt2px: function(len){
		// summary: converts points to pixels
		// len: Number: a value in points
		return len * dojox.gfx.px_in_pt();	// Number
	},
	px2pt: function(len){
		// summary: converts pixels to points
		// len: Number: a value in pixels
		return len / dojox.gfx.px_in_pt();	// Number
	},
	normalizedLength: function(len) {
		// summary: converts any length value to pixels
		// len: String: a length, e.g., "12pc"
		if(len.length == 0) return 0;
		if(len.length > 2){
			var px_in_pt = dojox.gfx.px_in_pt();
			var val = parseFloat(len);
			switch(len.slice(-2)){
				case "px": return val;
				case "pt": return val * px_in_pt;
				case "in": return val * 72 * px_in_pt;
				case "pc": return val * 12 * px_in_pt;
				case "mm": return val * dojox.gfx.mm_in_pt * px_in_pt;
				case "cm": return val * dojox.gfx.cm_in_pt * px_in_pt;
			}
		}
		return parseFloat(len);	// Number
	},

	// a constant used to split a SVG/VML path into primitive components
	pathVmlRegExp: /([A-Za-z]+)|(\d+(\.\d+)?)|(\.\d+)|(-\d+(\.\d+)?)|(-\.\d+)/g,
	pathSvgRegExp: /([A-Za-z])|(\d+(\.\d+)?)|(\.\d+)|(-\d+(\.\d+)?)|(-\.\d+)/g,

	equalSources: function(a, b){
		// summary: compares event sources, returns true if they are equal
		return a && b && a == b;
	}
});

}

if(!dojo._hasResource["dojox.gfx"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.gfx"] = true;
dojo.provide("dojox.gfx");




dojo.loadInit(function(){
	//Since loaderInit can be fired before any dojo.provide/require calls,
	//make sure the dojox.gfx object exists and only run this logic if dojox.gfx.renderer
	//has not been defined yet.
	var gfx = dojo.getObject("dojox.gfx", true), sl, flag, match;
	if(!gfx.renderer){
		var renderers = (typeof dojo.config.gfxRenderer == "string" ?
			dojo.config.gfxRenderer : "svg,vml,silverlight,canvas").split(",");

		// mobile platform detection
		// TODO: move to the base?

		var ua = navigator.userAgent, iPhoneOsBuild = 0, androidVersion = 0;
		if(dojo.isSafari >= 3){
			// detect mobile version of WebKit starting with "version 3"

			//	comprehensive iPhone test.  Have to figure out whether it's SVG or Canvas based on the build.
			//	iPhone OS build numbers from en.wikipedia.org.
			if(ua.indexOf("iPhone") >= 0 || ua.indexOf("iPod") >= 0){
				//	grab the build out of this.  Expression is a little nasty because we want
				//		to be sure we have the whole version string.
				match = ua.match(/Version\/(\d(\.\d)?(\.\d)?)\sMobile\/([^\s]*)\s?/);
				if(match){
					//	grab the build out of the match.  Only use the first three because of specific builds.
					iPhoneOsBuild = parseInt(match[4].substr(0,3), 16);
				}
			}
		}
		if(dojo.isWebKit){
			// Android detection
			if(!iPhoneOsBuild){
				match = ua.match(/Android\s+(\d+\.\d+)/);
				if(match){
					androidVersion = parseFloat(match[1]);
					// Android 1.0-1.1 doesn't support SVG but supports Canvas
				}
			}
		}

		for(var i = 0; i < renderers.length; ++i){
			switch(renderers[i]){
				case "svg":
					//	iPhone OS builds greater than 5F1 should have SVG.
					if(!dojo.isIE && (!iPhoneOsBuild || iPhoneOsBuild >= 0x5f1) && !androidVersion && !dojo.isAIR){
						dojox.gfx.renderer = "svg";
					}
					break;
				case "vml":
					if(dojo.isIE){
						dojox.gfx.renderer = "vml";
					}
					break;
				case "silverlight":
					try{
						if(dojo.isIE){
							sl = new ActiveXObject("AgControl.AgControl");
							if(sl && sl.IsVersionSupported("1.0")){
								flag = true;
							}
						}else{
							if(navigator.plugins["Silverlight Plug-In"]){
								flag = true;
							}
						}
					}catch(e){
						flag = false;
					}finally{
						sl = null;
					}
					if(flag){ dojox.gfx.renderer = "silverlight"; }
					break;
				case "canvas":
					//TODO: need more comprehensive test for Canvas
					if(!dojo.isIE){
						dojox.gfx.renderer = "canvas";
					}
					break;
			}
			if(dojox.gfx.renderer){ break; }
		}
		if(dojo.config.isDebug){
			
		}
	}
});

// include a renderer conditionally
dojo.requireIf(dojox.gfx.renderer == "svg", "dojox.gfx.svg");
dojo.requireIf(dojox.gfx.renderer == "vml", "dojox.gfx.vml");
dojo.requireIf(dojox.gfx.renderer == "silverlight", "dojox.gfx.silverlight");
dojo.requireIf(dojox.gfx.renderer == "canvas", "dojox.gfx.canvas");

}

if(!dojo._hasResource["dojox.lang.functional.lambda"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.functional.lambda"] = true;
dojo.provide("dojox.lang.functional.lambda");

// This module adds high-level functions and related constructs:
//	- anonymous functions built from the string

// Acknoledgements:
//	- lambda() is based on work by Oliver Steele 
//		(http://osteele.com/sources/javascript/functional/functional.js)
//		which was published under MIT License

// Notes:
//	- lambda() produces functions, which after the compilation step are 
//		as fast as regular JS functions (at least theoretically).

// Lambda input values:
//	- returns functions unchanged
//	- converts strings to functions
//	- converts arrays to a functional composition

(function(){
	var df = dojox.lang.functional, lcache = {};

	// split() is augmented on IE6 to ensure the uniform behavior
	var split = "ab".split(/a*/).length > 1 ? String.prototype.split :
			function(sep){
				 var r = this.split.call(this, sep),
					 m = sep.exec(this);
				 if(m && m.index == 0){ r.unshift(""); }
				 return r;
			};
			
	var lambda = function(/*String*/ s){
		var args = [], sects = split.call(s, /\s*->\s*/m);
		if(sects.length > 1){
			while(sects.length){
				s = sects.pop();
				args = sects.pop().split(/\s*,\s*|\s+/m);
				if(sects.length){ sects.push("(function(" + args + "){return (" + s + ")})"); }
			}
		}else if(s.match(/\b_\b/)){
			args = ["_"];
		}else{
			var l = s.match(/^\s*(?:[+*\/%&|\^\.=<>]|!=)/m),
				r = s.match(/[+\-*\/%&|\^\.=<>!]\s*$/m);
			if(l || r){
				if(l){
					args.push("$1");
					s = "$1" + s;
				}
				if(r){
					args.push("$2");
					s = s + "$2";
				}
			}else{
				// the point of the long regex below is to exclude all well-known 
				// lower-case words from the list of potential arguments
				var vars = s.
					replace(/(?:\b[A-Z]|\.[a-zA-Z_$])[a-zA-Z_$\d]*|[a-zA-Z_$][a-zA-Z_$\d]*:|this|true|false|null|undefined|typeof|instanceof|in|delete|new|void|arguments|decodeURI|decodeURIComponent|encodeURI|encodeURIComponent|escape|eval|isFinite|isNaN|parseFloat|parseInt|unescape|dojo|dijit|dojox|window|document|'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"/g, "").
					match(/([a-z_$][a-z_$\d]*)/gi) || [], t = {};
				dojo.forEach(vars, function(v){
					if(!(v in t)){
						args.push(v);
						t[v] = 1;
					}
				});
			}
		}
		return {args: args, body: s};	// Object
	};

	var compose = function(/*Array*/ a){
		return a.length ? 
					function(){
						var i = a.length - 1, x = df.lambda(a[i]).apply(this, arguments);
						for(--i; i >= 0; --i){ x = df.lambda(a[i]).call(this, x); }
						return x;
					}
				: 
					// identity
					function(x){ return x; };
	};

	dojo.mixin(df, {
		// lambda
		rawLambda: function(/*String*/ s){
			// summary:
			//		builds a function from a snippet, or array (composing),
			//		returns an object describing the function; functions are
			//		passed through unmodified.
			// description:
			//		This method is to normalize a functional representation (a
			//		text snippet) to an object that contains an array of
			//		arguments, and a body , which is used to calculate the
			//		returning value.
			return lambda(s);	// Object
		},
		buildLambda: function(/*String*/ s){
			// summary:
			//		builds a function from a snippet, returns a string, which
			//		represents the function.
			// description:
			//		This method returns a textual representation of a function
			//		built from the snippet. It is meant to be evaled in the
			//		proper context, so local variables can be pulled from the
			//		environment.
			s = lambda(s);
			return "function(" + s.args.join(",") + "){return (" + s.body + ");}";	// String
		},
		lambda: function(/*Function|String|Array*/ s){
			// summary:
			//		builds a function from a snippet, or array (composing),
			//		returns a function object; functions are passed through
			//		unmodified.
			// description:
			//		This method is used to normalize a functional
			//		representation (a text snippet, an array, or a function) to
			//		a function object.
			if(typeof s == "function"){ return s; }
			if(s instanceof Array){ return compose(s); }
			if(s in lcache){ return lcache[s]; }
			s = lambda(s);
			return lcache[s] = new Function(s.args, "return (" + s.body + ");");	// Function
		},
		clearLambdaCache: function(){
			// summary:
			//		clears internal cache of lambdas
			lcache = {};
		}
	});
})();

}

if(!dojo._hasResource["dojox.lang.functional.array"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.functional.array"] = true;
dojo.provide("dojox.lang.functional.array");



// This module adds high-level functions and related constructs:
//	- array-processing functions similar to standard JS functions

// Notes:
//	- this module provides JS standard methods similar to high-level functions in dojo/_base/array.js: 
//		forEach, map, filter, every, some

// Defined methods:
//	- take any valid lambda argument as the functional argument
//	- operate on dense arrays
//	- take a string as the array argument
//	- take an iterator objects as the array argument

(function(){
	var d = dojo, df = dojox.lang.functional, empty = {};

	d.mixin(df, {
		// JS 1.6 standard array functions, which can take a lambda as a parameter.
		// Consider using dojo._base.array functions, if you don't need the lambda support.
		filter: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: creates a new array with all elements that pass the test 
			//	implemented by the provided function.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var t = [], v, i, n;
			if(d.isArray(a)){
				// array
				for(i = 0, n = a.length; i < n; ++i){
					v = a[i];
					if(f.call(o, v, i, a)){ t.push(v); }
				}
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				for(i = 0; a.hasNext();){
					v = a.next();
					if(f.call(o, v, i++, a)){ t.push(v); }
				}
			}else{
				// object/dictionary
				for(i in a){
					if(!(i in empty)){
						v = a[i];
						if(f.call(o, v, i, a)){ t.push(v); }
					}
				}
			}
			return t;	// Array
		},
		forEach: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: executes a provided function once per array element.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var i, n;
			if(d.isArray(a)){
				// array
				for(i = 0, n = a.length; i < n; f.call(o, a[i], i, a), ++i);
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				for(i = 0; a.hasNext(); f.call(o, a.next(), i++, a));
			}else{
				// object/dictionary
				for(i in a){
					if(!(i in empty)){
						f.call(o, a[i], i, a);
					}
				}
			}
			return o;	// Object
		},
		map: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: creates a new array with the results of calling 
			//	a provided function on every element in this array.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var t, n, i;
			if(d.isArray(a)){
				// array
				t = new Array(n = a.length);
				for(i = 0; i < n; t[i] = f.call(o, a[i], i, a), ++i);
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				t = [];
				for(i = 0; a.hasNext(); t.push(f.call(o, a.next(), i++, a)));
			}else{
				// object/dictionary
				t = [];
				for(i in a){
					if(!(i in empty)){
						t.push(f.call(o, a[i], i, a));
					}
				}
			}
			return t;	// Array
		},
		every: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: tests whether all elements in the array pass the test 
			//	implemented by the provided function.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var i, n;
			if(d.isArray(a)){
				// array
				for(i = 0, n = a.length; i < n; ++i){
					if(!f.call(o, a[i], i, a)){
						return false;	// Boolean
					}
				}
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				for(i = 0; a.hasNext();){
					if(!f.call(o, a.next(), i++, a)){
						return false;	// Boolean
					}
				}
			}else{
				// object/dictionary
				for(i in a){
					if(!(i in empty)){
						if(!f.call(o, a[i], i, a)){
							return false;	// Boolean
						}
					}
				}
			}
			return true;	// Boolean
		},
		some: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: tests whether some element in the array passes the test 
			//	implemented by the provided function.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var i, n;
			if(d.isArray(a)){
				// array
				for(i = 0, n = a.length; i < n; ++i){
					if(f.call(o, a[i], i, a)){
						return true;	// Boolean
					}
				}
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				for(i = 0; a.hasNext();){
					if(f.call(o, a.next(), i++, a)){
						return true;	// Boolean
					}
				}
			}else{
				// object/dictionary
				for(i in a){
					if(!(i in empty)){
						if(f.call(o, a[i], i, a)){
							return true;	// Boolean
						}
					}
				}
			}
			return false;	// Boolean
		}
	});
})();

}

if(!dojo._hasResource["dojox.lang.functional.object"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.functional.object"] = true;
dojo.provide("dojox.lang.functional.object");



// This module adds high-level functions and related constructs:
//	- object/dictionary helpers

// Defined methods:
//	- take any valid lambda argument as the functional argument
//	- skip all attributes that are present in the empty object 
//		(IE and/or 3rd-party libraries).

(function(){
	var d = dojo, df = dojox.lang.functional, empty = {};

	d.mixin(df, {
		// object helpers
		keys: function(/*Object*/ obj){
			// summary: returns an array of all keys in the object
			var t = [];
			for(var i in obj){
				if(!(i in empty)){
					t.push(i);
				}
			}
			return	t; // Array
		},
		values: function(/*Object*/ obj){
			// summary: returns an array of all values in the object
			var t = [];
			for(var i in obj){
				if(!(i in empty)){
					t.push(obj[i]);
				}
			}
			return	t; // Array
		},
		filterIn: function(/*Object*/ obj, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: creates new object with all attributes that pass the test 
			//	implemented by the provided function.
			o = o || d.global; f = df.lambda(f);
			var t = {}, v, i;
			for(i in obj){
				if(!(i in empty)){
					v = obj[i];
					if(f.call(o, v, i, obj)){ t[i] = v; }
				}
			}
			return t;	// Object
		},
		forIn: function(/*Object*/ obj, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: iterates over all object attributes.
			o = o || d.global; f = df.lambda(f);
			for(var i in obj){
				if(!(i in empty)){
					f.call(o, obj[i], i, obj);
				}
			}
			return o;	// Object
		},
		mapIn: function(/*Object*/ obj, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: creates new object with the results of calling 
			//	a provided function on every attribute in this object.
			o = o || d.global; f = df.lambda(f);
			var t = {}, i;
			for(i in obj){
				if(!(i in empty)){
					t[i] = f.call(o, obj[i], i, obj);
				}
			}
			return t;	// Object
		}
	});
})();

}

if(!dojo._hasResource["dojox.lang.functional"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.functional"] = true;
dojo.provide("dojox.lang.functional");





}

if(!dojo._hasResource["dojox.lang.functional.fold"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.functional.fold"] = true;
dojo.provide("dojox.lang.functional.fold");



// This module adds high-level functions and related constructs:
//	- "fold" family of functions

// Notes:
//	- missing high-level functions are provided with the compatible API: 
//		foldl, foldl1, foldr, foldr1
//	- missing JS standard functions are provided with the compatible API: 
//		reduce, reduceRight
//	- the fold's counterpart: unfold

// Defined methods:
//	- take any valid lambda argument as the functional argument
//	- operate on dense arrays
//	- take a string as the array argument
//	- take an iterator objects as the array argument (only foldl, foldl1, and reduce)

(function(){
	var d = dojo, df = dojox.lang.functional, empty = {};

	d.mixin(df, {
		// classic reduce-class functions
		foldl: function(/*Array|String|Object*/ a, /*Function*/ f, /*Object*/ z, /*Object?*/ o){
			// summary: repeatedly applies a binary function to an array from left
			//	to right using a seed value as a starting point; returns the final
			//	value.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var i, n;
			if(d.isArray(a)){
				// array
				for(i = 0, n = a.length; i < n; z = f.call(o, z, a[i], i, a), ++i);
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				for(i = 0; a.hasNext(); z = f.call(o, z, a.next(), i++, a));
			}else{
				// object/dictionary
				for(i in a){
					if(!(i in empty)){
						z = f.call(o, z, a[i], i, a);
					}
				}
			}
			return z;	// Object
		},
		foldl1: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: repeatedly applies a binary function to an array from left
			//	to right; returns the final value.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var z, i, n;
			if(d.isArray(a)){
				// array
				z = a[0];
				for(i = 1, n = a.length; i < n; z = f.call(o, z, a[i], i, a), ++i);
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				if(a.hasNext()){
					z = a.next();
					for(i = 1; a.hasNext(); z = f.call(o, z, a.next(), i++, a));
				}
			}else{
				// object/dictionary
				var first = true;
				for(i in a){
					if(!(i in empty)){
						if(first){
							z = a[i];
							first = false;
						}else{
							z = f.call(o, z, a[i], i, a);
						}
					}
				}
			}
			return z;	// Object
		},
		foldr: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object*/ z, /*Object?*/ o){
			// summary: repeatedly applies a binary function to an array from right
			//	to left using a seed value as a starting point; returns the final 
			//	value.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			for(var i = a.length; i > 0; --i, z = f.call(o, z, a[i], i, a));
			return z;	// Object
		},
		foldr1: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: repeatedly applies a binary function to an array from right
			//	to left; returns the final value.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var n = a.length, z = a[n - 1], i = n - 1;
			for(; i > 0; --i, z = f.call(o, z, a[i], i, a));
			return z;	// Object
		},
		// JS 1.8 standard array functions, which can take a lambda as a parameter.
		reduce: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object?*/ z){
			// summary: apply a function simultaneously against two values of the array
			//	(from left-to-right) as to reduce it to a single value.
			return arguments.length < 3 ? df.foldl1(a, f) : df.foldl(a, f, z);	// Object
		},
		reduceRight: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object?*/ z){
			// summary: apply a function simultaneously against two values of the array
			//	(from right-to-left) as to reduce it to a single value.
			return arguments.length < 3 ? df.foldr1(a, f) : df.foldr(a, f, z);	// Object
		},
		// the fold's counterpart: unfold
		unfold: function(/*Function|String|Array*/ pr, /*Function|String|Array*/ f,
						/*Function|String|Array*/ g, /*Object*/ z, /*Object?*/ o){
			// summary: builds an array by unfolding a value
			o = o || d.global; f = df.lambda(f); g = df.lambda(g); pr = df.lambda(pr);
			var t = [];
			for(; !pr.call(o, z); t.push(f.call(o, z)), z = g.call(o, z));
			return t;	// Array
		}
	});
})();

}

if(!dojo._hasResource["dojox.lang.functional.reversed"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.functional.reversed"] = true;
dojo.provide("dojox.lang.functional.reversed");



// This module adds high-level functions and related constructs:
//	- reversed versions of array-processing functions similar to standard JS functions

// Notes:
//	- this module provides reversed versions of standard array-processing functions: 
//		forEachRev, mapRev, filterRev

// Defined methods:
//	- take any valid lambda argument as the functional argument
//	- operate on dense arrays
//	- take a string as the array argument

(function(){
	var d = dojo, df = dojox.lang.functional;

	d.mixin(df, {
		// JS 1.6 standard array functions, which can take a lambda as a parameter.
		// Consider using dojo._base.array functions, if you don't need the lambda support.
		filterRev: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: creates a new array with all elements that pass the test 
			//	implemented by the provided function.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var t = [], v, i = a.length - 1;
			for(; i >= 0; --i){
				v = a[i];
				if(f.call(o, v, i, a)){ t.push(v); }
			}
			return t;	// Array
		},
		forEachRev: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: executes a provided function once per array element.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			for(var i = a.length - 1; i >= 0; f.call(o, a[i], i, a), --i);
		},
		mapRev: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: creates a new array with the results of calling 
			//	a provided function on every element in this array.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var n = a.length, t = new Array(n), i = n - 1, j = 0;
			for(; i >= 0; t[j++] = f.call(o, a[i], i, a), --i);
			return t;	// Array
		},
		everyRev: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: tests whether all elements in the array pass the test 
			//	implemented by the provided function.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			for(var i = a.length - 1; i >= 0; --i){
				if(!f.call(o, a[i], i, a)){
					return false;	// Boolean
				}
			}
			return true;	// Boolean
		},
		someRev: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: tests whether some element in the array passes the test 
			//	implemented by the provided function.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			for(var i = a.length - 1; i >= 0; --i){
				if(f.call(o, a[i], i, a)){
					return true;	// Boolean
				}
			}
			return false;	// Boolean
		}
	});
})();

}

if(!dojo._hasResource["dojo.colors"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojo.colors"] = true;
dojo.provide("dojo.colors");

//TODO: this module appears to break naming conventions

/*=====
dojo.colors = {
	// summary: Color utilities
}
=====*/

(function(){
	// this is a standard conversion prescribed by the CSS3 Color Module
	var hue2rgb = function(m1, m2, h){
		if(h < 0){ ++h; }
		if(h > 1){ --h; }
		var h6 = 6 * h;
		if(h6 < 1){ return m1 + (m2 - m1) * h6; }
		if(2 * h < 1){ return m2; }
		if(3 * h < 2){ return m1 + (m2 - m1) * (2 / 3 - h) * 6; }
		return m1;
	};
	
	dojo.colorFromRgb = function(/*String*/ color, /*dojo.Color?*/ obj){
		// summary:
		//		get rgb(a) array from css-style color declarations
		// description:
		//		this function can handle all 4 CSS3 Color Module formats: rgb,
		//		rgba, hsl, hsla, including rgb(a) with percentage values.
		var m = color.toLowerCase().match(/^(rgba?|hsla?)\(([\s\.\-,%0-9]+)\)/);
		if(m){
			var c = m[2].split(/\s*,\s*/), l = c.length, t = m[1], a;
			if((t == "rgb" && l == 3) || (t == "rgba" && l == 4)){
				var r = c[0];
				if(r.charAt(r.length - 1) == "%"){
					// 3 rgb percentage values
					a = dojo.map(c, function(x){
						return parseFloat(x) * 2.56;
					});
					if(l == 4){ a[3] = c[3]; }
					return dojo.colorFromArray(a, obj);	// dojo.Color
				}
				return dojo.colorFromArray(c, obj);	// dojo.Color
			}
			if((t == "hsl" && l == 3) || (t == "hsla" && l == 4)){
				// normalize hsl values
				var H = ((parseFloat(c[0]) % 360) + 360) % 360 / 360,
					S = parseFloat(c[1]) / 100,
					L = parseFloat(c[2]) / 100,
					// calculate rgb according to the algorithm 
					// recommended by the CSS3 Color Module 
					m2 = L <= 0.5 ? L * (S + 1) : L + S - L * S, 
					m1 = 2 * L - m2;
				a = [
					hue2rgb(m1, m2, H + 1 / 3) * 256,
					hue2rgb(m1, m2, H) * 256,
					hue2rgb(m1, m2, H - 1 / 3) * 256,
					1
				];
				if(l == 4){ a[3] = c[3]; }
				return dojo.colorFromArray(a, obj);	// dojo.Color
			}
		}
		return null;	// dojo.Color
	};
	
	var confine = function(c, low, high){
		// summary:
		//		sanitize a color component by making sure it is a number,
		//		and clamping it to valid values
		c = Number(c);
		return isNaN(c) ? high : c < low ? low : c > high ? high : c;	// Number
	};
	
	dojo.Color.prototype.sanitize = function(){
		// summary: makes sure that the object has correct attributes
		var t = this;
		t.r = Math.round(confine(t.r, 0, 255));
		t.g = Math.round(confine(t.g, 0, 255));
		t.b = Math.round(confine(t.b, 0, 255));
		t.a = confine(t.a, 0, 1);
		return this;	// dojo.Color
	};
})();


dojo.colors.makeGrey = function(/*Number*/ g, /*Number?*/ a){
	// summary: creates a greyscale color with an optional alpha
	return dojo.colorFromArray([g, g, g, a]);
};

// mixin all CSS3 named colors not already in _base, along with SVG 1.0 variant spellings
dojo.mixin(dojo.Color.named, {
	aliceblue:	[240,248,255],
	antiquewhite:	[250,235,215],
	aquamarine:	[127,255,212],
	azure:	[240,255,255],
	beige:	[245,245,220],
	bisque:	[255,228,196],
	blanchedalmond:	[255,235,205],
	blueviolet:	[138,43,226],
	brown:	[165,42,42],
	burlywood:	[222,184,135],
	cadetblue:	[95,158,160],
	chartreuse:	[127,255,0],
	chocolate:	[210,105,30],
	coral:	[255,127,80],
	cornflowerblue:	[100,149,237],
	cornsilk:	[255,248,220],
	crimson:	[220,20,60],
	cyan:	[0,255,255],
	darkblue:	[0,0,139],
	darkcyan:	[0,139,139],
	darkgoldenrod:	[184,134,11],
	darkgray:	[169,169,169],
	darkgreen:	[0,100,0],
	darkgrey:	[169,169,169],
	darkkhaki:	[189,183,107],
	darkmagenta:	[139,0,139],
	darkolivegreen:	[85,107,47],
	darkorange:	[255,140,0],
	darkorchid:	[153,50,204],
	darkred:	[139,0,0],
	darksalmon:	[233,150,122],
	darkseagreen:	[143,188,143],
	darkslateblue:	[72,61,139],
	darkslategray:	[47,79,79],
	darkslategrey:	[47,79,79],
	darkturquoise:	[0,206,209],
	darkviolet:	[148,0,211],
	deeppink:	[255,20,147],
	deepskyblue:	[0,191,255],
	dimgray:	[105,105,105],
	dimgrey:	[105,105,105],
	dodgerblue:	[30,144,255],
	firebrick:	[178,34,34],
	floralwhite:	[255,250,240],
	forestgreen:	[34,139,34],
	gainsboro:	[220,220,220],
	ghostwhite:	[248,248,255],
	gold:	[255,215,0],
	goldenrod:	[218,165,32],
	greenyellow:	[173,255,47],
	grey:	[128,128,128],
	honeydew:	[240,255,240],
	hotpink:	[255,105,180],
	indianred:	[205,92,92],
	indigo:	[75,0,130],
	ivory:	[255,255,240],
	khaki:	[240,230,140],
	lavender:	[230,230,250],
	lavenderblush:	[255,240,245],
	lawngreen:	[124,252,0],
	lemonchiffon:	[255,250,205],
	lightblue:	[173,216,230],
	lightcoral:	[240,128,128],
	lightcyan:	[224,255,255],
	lightgoldenrodyellow:	[250,250,210],
	lightgray:	[211,211,211],
	lightgreen:	[144,238,144],
	lightgrey:	[211,211,211],
	lightpink:	[255,182,193],
	lightsalmon:	[255,160,122],
	lightseagreen:	[32,178,170],
	lightskyblue:	[135,206,250],
	lightslategray:	[119,136,153],
	lightslategrey:	[119,136,153],
	lightsteelblue:	[176,196,222],
	lightyellow:	[255,255,224],
	limegreen:	[50,205,50],
	linen:	[250,240,230],
	magenta:	[255,0,255],
	mediumaquamarine:	[102,205,170],
	mediumblue:	[0,0,205],
	mediumorchid:	[186,85,211],
	mediumpurple:	[147,112,219],
	mediumseagreen:	[60,179,113],
	mediumslateblue:	[123,104,238],
	mediumspringgreen:	[0,250,154],
	mediumturquoise:	[72,209,204],
	mediumvioletred:	[199,21,133],
	midnightblue:	[25,25,112],
	mintcream:	[245,255,250],
	mistyrose:	[255,228,225],
	moccasin:	[255,228,181],
	navajowhite:	[255,222,173],
	oldlace:	[253,245,230],
	olivedrab:	[107,142,35],
	orange:	[255,165,0],
	orangered:	[255,69,0],
	orchid:	[218,112,214],
	palegoldenrod:	[238,232,170],
	palegreen:	[152,251,152],
	paleturquoise:	[175,238,238],
	palevioletred:	[219,112,147],
	papayawhip:	[255,239,213],
	peachpuff:	[255,218,185],
	peru:	[205,133,63],
	pink:	[255,192,203],
	plum:	[221,160,221],
	powderblue:	[176,224,230],
	rosybrown:	[188,143,143],
	royalblue:	[65,105,225],
	saddlebrown:	[139,69,19],
	salmon:	[250,128,114],
	sandybrown:	[244,164,96],
	seagreen:	[46,139,87],
	seashell:	[255,245,238],
	sienna:	[160,82,45],
	skyblue:	[135,206,235],
	slateblue:	[106,90,205],
	slategray:	[112,128,144],
	slategrey:	[112,128,144],
	snow:	[255,250,250],
	springgreen:	[0,255,127],
	steelblue:	[70,130,180],
	tan:	[210,180,140],
	thistle:	[216,191,216],
	tomato:	[255,99,71],
	transparent: [0, 0, 0, 0],
	turquoise:	[64,224,208],
	violet:	[238,130,238],
	wheat:	[245,222,179],
	whitesmoke:	[245,245,245],
	yellowgreen:	[154,205,50]
});

}

if(!dojo._hasResource["dojox.color._base"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.color._base"] = true;
dojo.provide("dojox.color._base");


//	alias all the dojo.Color mechanisms
dojox.color.Color=dojo.Color;
dojox.color.blend=dojo.blendColors;
dojox.color.fromRgb=dojo.colorFromRgb;
dojox.color.fromHex=dojo.colorFromHex;
dojox.color.fromArray=dojo.colorFromArray;
dojox.color.fromString=dojo.colorFromString;

//	alias the dojo.colors mechanisms
dojox.color.greyscale=dojo.colors.makeGrey;

//	static methods
dojo.mixin(dojox.color, {
	fromCmy: function(/* Object|Array|int */cyan, /*int*/magenta, /*int*/yellow){
		//	summary
		//	Create a dojox.color.Color from a CMY defined color.
		//	All colors should be expressed as 0-100 (percentage)

		if(dojo.isArray(cyan)){
			magenta=cyan[1], yellow=cyan[2], cyan=cyan[0];
		} else if(dojo.isObject(cyan)){
			magenta=cyan.m, yellow=cyan.y, cyan=cyan.c;
		}
		cyan/=100, magenta/=100, yellow/=100;

		var r=1-cyan, g=1-magenta, b=1-yellow;
		return new dojox.color.Color({ r:Math.round(r*255), g:Math.round(g*255), b:Math.round(b*255) });	//	dojox.color.Color
	},

	fromCmyk: function(/* Object|Array|int */cyan, /*int*/magenta, /*int*/yellow, /*int*/black){
		//	summary
		//	Create a dojox.color.Color from a CMYK defined color.
		//	All colors should be expressed as 0-100 (percentage)

		if(dojo.isArray(cyan)){
			magenta=cyan[1], yellow=cyan[2], black=cyan[3], cyan=cyan[0];
		} else if(dojo.isObject(cyan)){
			magenta=cyan.m, yellow=cyan.y, black=cyan.b, cyan=cyan.c;
		}
		cyan/=100, magenta/=100, yellow/=100, black/=100;
		var r,g,b;
		r = 1-Math.min(1, cyan*(1-black)+black);
		g = 1-Math.min(1, magenta*(1-black)+black);
		b = 1-Math.min(1, yellow*(1-black)+black);
		return new dojox.color.Color({ r:Math.round(r*255), g:Math.round(g*255), b:Math.round(b*255) });	//	dojox.color.Color
	},
	
	fromHsl: function(/* Object|Array|int */hue, /* int */saturation, /* int */luminosity){
		//	summary
		//	Create a dojox.color.Color from an HSL defined color.
		//	hue from 0-359 (degrees), saturation and luminosity 0-100.

		if(dojo.isArray(hue)){
			saturation=hue[1], luminosity=hue[2], hue=hue[0];
		} else if(dojo.isObject(hue)){
			saturation=hue.s, luminosity=hue.l, hue=hue.h;
		}
		saturation/=100;
		luminosity/=100;

		while(hue<0){ hue+=360; }
		while(hue>=360){ hue-=360; }
		
		var r, g, b;
		if(hue<120){
			r=(120-hue)/60, g=hue/60, b=0;
		} else if (hue<240){
			r=0, g=(240-hue)/60, b=(hue-120)/60;
		} else {
			r=(hue-240)/60, g=0, b=(360-hue)/60;
		}
		
		r=2*saturation*Math.min(r, 1)+(1-saturation);
		g=2*saturation*Math.min(g, 1)+(1-saturation);
		b=2*saturation*Math.min(b, 1)+(1-saturation);
		if(luminosity<0.5){
			r*=luminosity, g*=luminosity, b*=luminosity;
		}else{
			r=(1-luminosity)*r+2*luminosity-1;
			g=(1-luminosity)*g+2*luminosity-1;
			b=(1-luminosity)*b+2*luminosity-1;
		}
		return new dojox.color.Color({ r:Math.round(r*255), g:Math.round(g*255), b:Math.round(b*255) });	//	dojox.color.Color
	},
	
	fromHsv: function(/* Object|Array|int */hue, /* int */saturation, /* int */value){
		//	summary
		//	Create a dojox.color.Color from an HSV defined color.
		//	hue from 0-359 (degrees), saturation and value 0-100.

		if(dojo.isArray(hue)){
			saturation=hue[1], value=hue[2], hue=hue[0];
		} else if (dojo.isObject(hue)){
			saturation=hue.s, value=hue.v, hue=hue.h;
		}
		
		if(hue==360){ hue=0; }
		saturation/=100;
		value/=100;
		
		var r, g, b;
		if(saturation==0){
			r=value, b=value, g=value;
		}else{
			var hTemp=hue/60, i=Math.floor(hTemp), f=hTemp-i;
			var p=value*(1-saturation);
			var q=value*(1-(saturation*f));
			var t=value*(1-(saturation*(1-f)));
			switch(i){
				case 0:{ r=value, g=t, b=p; break; }
				case 1:{ r=q, g=value, b=p; break; }
				case 2:{ r=p, g=value, b=t; break; }
				case 3:{ r=p, g=q, b=value; break; }
				case 4:{ r=t, g=p, b=value; break; }
				case 5:{ r=value, g=p, b=q; break; }
			}
		}
		return new dojox.color.Color({ r:Math.round(r*255), g:Math.round(g*255), b:Math.round(b*255) });	//	dojox.color.Color
	}
});

//	Conversions directly on dojox.color.Color
dojo.extend(dojox.color.Color, {
	toCmy: function(){
		//	summary
		//	Convert this Color to a CMY definition.
		var cyan=1-(this.r/255), magenta=1-(this.g/255), yellow=1-(this.b/255);
		return { c:Math.round(cyan*100), m:Math.round(magenta*100), y:Math.round(yellow*100) };		//	Object
	},
	
	toCmyk: function(){
		//	summary
		//	Convert this Color to a CMYK definition.
		var cyan, magenta, yellow, black;
		var r=this.r/255, g=this.g/255, b=this.b/255;
		black = Math.min(1-r, 1-g, 1-b);
		cyan = (1-r-black)/(1-black);
		magenta = (1-g-black)/(1-black);
		yellow = (1-b-black)/(1-black);
		return { c:Math.round(cyan*100), m:Math.round(magenta*100), y:Math.round(yellow*100), b:Math.round(black*100) };	//	Object
	},
	
	toHsl: function(){
		//	summary
		//	Convert this Color to an HSL definition.
		var r=this.r/255, g=this.g/255, b=this.b/255;
		var min = Math.min(r, b, g), max = Math.max(r, g, b);
		var delta = max-min;
		var h=0, s=0, l=(min+max)/2;
		if(l>0 && l<1){
			s = delta/((l<0.5)?(2*l):(2-2*l));
		}
		if(delta>0){
			if(max==r && max!=g){
				h+=(g-b)/delta;
			}
			if(max==g && max!=b){
				h+=(2+(b-r)/delta);
			}
			if(max==b && max!=r){
				h+=(4+(r-g)/delta);
			}
			h*=60;
		}
		return { h:h, s:Math.round(s*100), l:Math.round(l*100) };	//	Object
	},

	toHsv: function(){
		//	summary
		//	Convert this Color to an HSV definition.
		var r=this.r/255, g=this.g/255, b=this.b/255;
		var min = Math.min(r, b, g), max = Math.max(r, g, b);
		var delta = max-min;
		var h = null, s = (max==0)?0:(delta/max);
		if(s==0){
			h = 0;
		}else{
			if(r==max){
				h = 60*(g-b)/delta;
			}else if(g==max){
				h = 120 + 60*(b-r)/delta;
			}else{
				h = 240 + 60*(r-g)/delta;
			}

			if(h<0){ h+=360; }
		}
		return { h:h, s:Math.round(s*100), v:Math.round(max*100) };	//	Object
	}
});

}

if(!dojo._hasResource["dojox.color"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.color"] = true;
dojo.provide("dojox.color");


}

if(!dojo._hasResource["dojox.color.Palette"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.color.Palette"] = true;
dojo.provide("dojox.color.Palette");


(function(){
	var dxc = dojox.color;
	/***************************************************************
	*	dojox.color.Palette
	*
	*	The Palette object is loosely based on the color palettes
	*	at Kuler (http://kuler.adobe.com).  They are 5 color palettes
	*	with the base color considered to be the third color in the
	*	palette (for generation purposes).
	*
	*	Palettes can be generated from well-known algorithms or they
	* 	can be manually created by passing an array to the constructor.
	*
	*	Palettes can be transformed, using a set of specific params
	*	similar to the way shapes can be transformed with dojox.gfx.
	*	However, unlike with transformations in dojox.gfx, transforming
	* 	a palette will return you a new Palette object, in effect
	* 	a clone of the original.
	***************************************************************/

	//	ctor ----------------------------------------------------------------------------
	dxc.Palette = function(/* String|Array|dojox.color.Color|dojox.color.Palette */base){
		//	summary
		//		An object that represents a palette of colors.
		//	description
		//		A Palette is a representation of a set of colors.  While the standard
		//		number of colors contained in a palette is 5, it can really handle any
		//		number of colors.
		//
		//		A palette is useful for the ability to transform all the colors in it
		//		using a simple object-based approach.  In addition, you can generate
		//		palettes using dojox.color.Palette.generate; these generated palettes
		//		are based on the palette generators at http://kuler.adobe.com.
		//
		//	colors: dojox.color.Color[]
		//		The actual color references in this palette.
		this.colors = [];
		if(base instanceof dojox.color.Palette){
			this.colors = base.colors.slice(0);
		}
		else if(base instanceof dojox.color.Color){
			this.colors = [ null, null, base, null, null ];
		}
		else if(dojo.isArray(base)){
			this.colors = dojo.map(base.slice(0), function(item){
				if(dojo.isString(item)){ return new dojox.color.Color(item); }
				return item;
			});
		}
		else if (dojo.isString(base)){
			this.colors = [ null, null, new dojox.color.Color(base), null, null ];
		}
	}

	//	private functions ---------------------------------------------------------------

	//	transformations
	function tRGBA(p, param, val){
		var ret = new dojox.color.Palette();
		ret.colors = [];
		dojo.forEach(p.colors, function(item){
			var r=(param=="dr")?item.r+val:item.r,
				g=(param=="dg")?item.g+val:item.g,
				b=(param=="db")?item.b+val:item.b,
				a=(param=="da")?item.a+val:item.a
			ret.colors.push(new dojox.color.Color({
				r: Math.min(255, Math.max(0, r)),
				g: Math.min(255, Math.max(0, g)),
				b: Math.min(255, Math.max(0, b)),
				a: Math.min(1, Math.max(0, a))
			}));
		});
		
		return ret;
	}

	function tCMY(p, param, val){
		var ret = new dojox.color.Palette();
		ret.colors = [];
		dojo.forEach(p.colors, function(item){
			var o=item.toCmy(), 
				c=(param=="dc")?o.c+val:o.c,
				m=(param=="dm")?o.m+val:o.m,
				y=(param=="dy")?o.y+val:o.y;
			ret.colors.push(dojox.color.fromCmy(
				Math.min(100, Math.max(0, c)),
				Math.min(100, Math.max(0, m)),
				Math.min(100, Math.max(0, y))
			));
		});
		return ret;
	}

	function tCMYK(p, param, val){
		var ret = new dojox.color.Palette();
		ret.colors = [];
		dojo.forEach(p.colors, function(item){
			var o=item.toCmyk(), 
				c=(param=="dc")?o.c+val:o.c,
				m=(param=="dm")?o.m+val:o.m,
				y=(param=="dy")?o.y+val:o.y,
				k=(param=="dk")?o.b+val:o.b;
			ret.colors.push(dojox.color.fromCmyk(
				Math.min(100, Math.max(0, c)),
				Math.min(100, Math.max(0, m)),
				Math.min(100, Math.max(0, y)),
				Math.min(100, Math.max(0, k))
			));
		});
		return ret;
	}

	function tHSL(p, param, val){
		var ret = new dojox.color.Palette();
		ret.colors = [];
		dojo.forEach(p.colors, function(item){
			var o=item.toHsl(), 
				h=(param=="dh")?o.h+val:o.h,
				s=(param=="ds")?o.s+val:o.s,
				l=(param=="dl")?o.l+val:o.l;
			ret.colors.push(dojox.color.fromHsl(h%360, Math.min(100, Math.max(0, s)), Math.min(100, Math.max(0, l))));
		});
		return ret;
	}

	function tHSV(p, param, val){
		var ret = new dojox.color.Palette();
		ret.colors = [];
		dojo.forEach(p.colors, function(item){
			var o=item.toHsv(), 
				h=(param=="dh")?o.h+val:o.h,
				s=(param=="ds")?o.s+val:o.s,
				v=(param=="dv")?o.v+val:o.v;
			ret.colors.push(dojox.color.fromHsv(h%360, Math.min(100, Math.max(0, s)), Math.min(100, Math.max(0, v))));
		});
		return ret;
	}

	//	helper functions
	function rangeDiff(val, low, high){
		//	given the value in a range from 0 to high, find the equiv
		//		using the range low to high.
		return high-((high-val)*((high-low)/high));
	}

	//	object methods ---------------------------------------------------------------
	dojo.extend(dxc.Palette, {
		transform: function(/* Object */kwArgs){
			//	summary
			//		Transform the palette using a specific transformation function
			//		and a set of transformation parameters.
			//	description
			//		{palette}.transform is a simple way to uniformly transform
			//		all of the colors in a palette using any of 5 formulae:
			//		RGBA, HSL, HSV, CMYK or CMY.
			//
			//		Once the forumula to be used is determined, you can pass any
			//		number of parameters based on the formula "d"[param]; for instance,
			//		{ use: "rgba", dr: 20, dg: -50 } will take all of the colors in
			//		palette, add 20 to the R value and subtract 50 from the G value.
			//
			//		Unlike other types of transformations, transform does *not* alter
			//		the original palette but will instead return a new one.
			var fn=tRGBA;	//	the default transform function.
			if(kwArgs.use){
				//	we are being specific about the algo we want to use.
				var use=kwArgs.use.toLowerCase();
				if(use.indexOf("hs")==0){
					if(use.charAt(2)=="l"){ fn=tHSL; }
					else { fn=tHSV; }
				}
				else if(use.indexOf("cmy")==0){
					if(use.charAt(3)=="k"){ fn=tCMYK; }
					else { fn=tCMY; }
				}
			}
			//	try to guess the best choice.
			else if("dc" in kwArgs || "dm" in kwArgs || "dy" in kwArgs){
				if("dk" in kwArgs){ fn = tCMYK; }
				else { fn = tCMY; }
			}
			else if("dh" in kwArgs || "ds" in kwArgs){
				if("dv" in kwArgs){ fn = tHSV; }
				else { fn = tHSL; }
			}

			var palette = this;
			for(var p in kwArgs){
				//	ignore use
				if(p=="use"){ continue; }
				palette = fn(palette, p, kwArgs[p]);
			}
			return palette;		//	dojox.color.Palette
		},
		clone: function(){
			//	summary
			//		Clones the current palette.
			return new dxc.Palette(this);	//	dojox.color.Palette
		}
	});

	//	static methods ---------------------------------------------------------------
	dojo.mixin(dxc.Palette, {
		generators: {
			analogous:function(/* Object */args){
				var high=args.high||60, 	//	delta between base hue and highest hue (subtracted from base)
					low=args.low||18,		//	delta between base hue and lowest hue (added to base)
					base = dojo.isString(args.base)?new dojox.color.Color(args.base):args.base,
					hsv=base.toHsv();

				//	generate our hue angle differences
				var h=[
					(hsv.h+low+360)%360,
					(hsv.h+Math.round(low/2)+360)%360,
					hsv.h,
					(hsv.h-Math.round(high/2)+360)%360,
					(hsv.h-high+360)%360
				];

				var s1=Math.max(10, (hsv.s<=95)?hsv.s+5:(100-(hsv.s-95))),
					s2=(hsv.s>1)?hsv.s-1:21-hsv.s,
					v1=(hsv.v>=92)?hsv.v-9:Math.max(hsv.v+9, 20),
					v2=(hsv.v<=90)?Math.max(hsv.v+5, 20):(95+Math.ceil((hsv.v-90)/2)),
					s=[ s1, s2, hsv.s, s1, s1 ],
					v=[ v1, v2, hsv.v, v1, v2 ]

				return new dxc.Palette(dojo.map(h, function(hue, i){
					return dojox.color.fromHsv(hue, s[i], v[i]);
				}));		//	dojox.color.Palette
			},

			monochromatic: function(/* Object */args){
				var base = dojo.isString(args.base)?new dojox.color.Color(args.base):args.base,
					hsv = base.toHsv();
				
				//	figure out the saturation and value
				var s1 = (hsv.s-30>9)?hsv.s-30:hsv.s+30,
					s2 = hsv.s,
					v1 = rangeDiff(hsv.v, 20, 100),
					v2 = (hsv.v-20>20)?hsv.v-20:hsv.v+60,
					v3 = (hsv.v-50>20)?hsv.v-50:hsv.v+30;

				return new dxc.Palette([
					dojox.color.fromHsv(hsv.h, s1, v1),
					dojox.color.fromHsv(hsv.h, s2, v3),
					base,
					dojox.color.fromHsv(hsv.h, s1, v3),
					dojox.color.fromHsv(hsv.h, s2, v2)
				]);		//	dojox.color.Palette
			},

			triadic: function(/* Object */args){
				var base = dojo.isString(args.base)?new dojox.color.Color(args.base):args.base,
					hsv = base.toHsv();

				var h1 = (hsv.h+57+360)%360,
					h2 = (hsv.h-157+360)%360,
					s1 = (hsv.s>20)?hsv.s-10:hsv.s+10,
					s2 = (hsv.s>90)?hsv.s-10:hsv.s+10,
					s3 = (hsv.s>95)?hsv.s-5:hsv.s+5,
					v1 = (hsv.v-20>20)?hsv.v-20:hsv.v+20,
					v2 = (hsv.v-30>20)?hsv.v-30:hsv.v+30,
					v3 = (hsv.v-30>70)?hsv.v-30:hsv.v+30;

				return new dxc.Palette([
					dojox.color.fromHsv(h1, s1, hsv.v),
					dojox.color.fromHsv(hsv.h, s2, v2),
					base,
					dojox.color.fromHsv(h2, s2, v1),
					dojox.color.fromHsv(h2, s3, v3)
				]);		//	dojox.color.Palette
			},

			complementary: function(/* Object */args){
				var base = dojo.isString(args.base)?new dojox.color.Color(args.base):args.base,
					hsv = base.toHsv();

				var h1 = ((hsv.h*2)+137<360)?(hsv.h*2)+137:Math.floor(hsv.h/2)-137,
					s1 = Math.max(hsv.s-10, 0),
					s2 = rangeDiff(hsv.s, 10, 100),
					s3 = Math.min(100, hsv.s+20),
					v1 = Math.min(100, hsv.v+30),
					v2 = (hsv.v>20)?hsv.v-30:hsv.v+30;

				return new dxc.Palette([
					dojox.color.fromHsv(hsv.h, s1, v1),
					dojox.color.fromHsv(hsv.h, s2, v2),
					base,
					dojox.color.fromHsv(h1, s3, v2),
					dojox.color.fromHsv(h1, hsv.s, hsv.v)
				]);		//	dojox.color.Palette
			},

			splitComplementary: function(/* Object */args){
				var base = dojo.isString(args.base)?new dojox.color.Color(args.base):args.base,
					dangle = args.da || 30,
					hsv = base.toHsv();

				var baseh = ((hsv.h*2)+137<360)?(hsv.h*2)+137:Math.floor(hsv.h/2)-137,
					h1 = (baseh-dangle+360)%360,
					h2 = (baseh+dangle)%360,
					s1 = Math.max(hsv.s-10, 0),
					s2 = rangeDiff(hsv.s, 10, 100),
					s3 = Math.min(100, hsv.s+20),
					v1 = Math.min(100, hsv.v+30),
					v2 = (hsv.v>20)?hsv.v-30:hsv.v+30;

				return new dxc.Palette([
					dojox.color.fromHsv(h1, s1, v1),
					dojox.color.fromHsv(h1, s2, v2),
					base,
					dojox.color.fromHsv(h2, s3, v2),
					dojox.color.fromHsv(h2, hsv.s, hsv.v)
				]);		//	dojox.color.Palette
			},

			compound: function(/* Object */args){
				var base = dojo.isString(args.base)?new dojox.color.Color(args.base):args.base,
					hsv = base.toHsv();

				var h1 = ((hsv.h*2)+18<360)?(hsv.h*2)+18:Math.floor(hsv.h/2)-18,
					h2 = ((hsv.h*2)+120<360)?(hsv.h*2)+120:Math.floor(hsv.h/2)-120,
					h3 = ((hsv.h*2)+99<360)?(hsv.h*2)+99:Math.floor(hsv.h/2)-99,
					s1 = (hsv.s-40>10)?hsv.s-40:hsv.s+40,
					s2 = (hsv.s-10>80)?hsv.s-10:hsv.s+10,
					s3 = (hsv.s-25>10)?hsv.s-25:hsv.s+25,
					v1 = (hsv.v-40>10)?hsv.v-40:hsv.v+40,
					v2 = (hsv.v-20>80)?hsv.v-20:hsv.v+20,
					v3 = Math.max(hsv.v, 20);

				return new dxc.Palette([
					dojox.color.fromHsv(h1, s1, v1),
					dojox.color.fromHsv(h1, s2, v2),
					base,
					dojox.color.fromHsv(h2, s3, v3),
					dojox.color.fromHsv(h3, s2, v2)
				]);		//	dojox.color.Palette
			},

			shades: function(/* Object */args){
				var base = dojo.isString(args.base)?new dojox.color.Color(args.base):args.base,
					hsv = base.toHsv();

				var s  = (hsv.s==100 && hsv.v==0)?0:hsv.s,
					v1 = (hsv.v-50>20)?hsv.v-50:hsv.v+30,
					v2 = (hsv.v-25>=20)?hsv.v-25:hsv.v+55,
					v3 = (hsv.v-75>=20)?hsv.v-75:hsv.v+5,
					v4 = Math.max(hsv.v-10, 20);

				return new dxc.Palette([
					new dojox.color.fromHsv(hsv.h, s, v1),
					new dojox.color.fromHsv(hsv.h, s, v2),
					base,
					new dojox.color.fromHsv(hsv.h, s, v3),
					new dojox.color.fromHsv(hsv.h, s, v4)
				]);		//	dojox.color.Palette
			}
		},
		generate: function(/* String|dojox.color.Color */base, /* Function|String */type){
			//	summary
			//		Generate a new Palette using any of the named functions in
			//		dojox.color.Palette.generators or an optional function definition.
			if(dojo.isFunction(type)){
				return type({ base: base });	//	dojox.color.Palette
			}
			else if(dxc.Palette.generators[type]){
				return dxc.Palette.generators[type]({ base: base });	//	dojox.color.Palette
			}
			throw new Error("dojox.color.Palette.generate: the specified generator ('" + type + "') does not exist.");
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.Theme"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.Theme"] = true;
dojo.provide("dojox.charting.Theme");



(function(){
	var dxc=dojox.charting;
	//	TODO: Legend information

	dxc.Theme = function(/*Object?*/ kwArgs){
		kwArgs=kwArgs||{};
		var def = dxc.Theme._def;
		dojo.forEach(["chart", "plotarea", "axis", "series", "marker"], function(n){
			this[n] = dojo.delegate(def[n], kwArgs[n]||{});
		}, this);
		this.markers = dojo.delegate(dxc.Theme.Markers, kwArgs.markers||{});
		this.colors = [];
		this.antiAlias = ("antiAlias" in kwArgs)?kwArgs.antiAlias:true;
		this.assignColors = ("assignColors" in kwArgs)?kwArgs.assignColors:true;
		this.assignMarkers = ("assignMarkers" in kwArgs)?kwArgs.assignMarkers:true;

		//	push the colors, use _def colors if none passed.
		kwArgs.colors = kwArgs.colors||def.colors;
		dojo.forEach(kwArgs.colors, function(item){ 
			this.colors.push(item); 
		}, this);

		//	private variables for color and marker indexing
		this._current = { color:0, marker: 0 };
		this._markers = [];
		this._buildMarkerArray();
	};

	//	"static" fields
	//	default markers.
	//	A marker is defined by an SVG path segment; it should be defined as
	//		relative motion, and with the assumption that the path segment
	//		will be moved to the value point (i.e prepend Mx,y)
	dxc.Theme.Markers={
		CIRCLE:		"m-3,0 c0,-4 6,-4 6,0 m-6,0 c0,4 6,4 6,0", 
		SQUARE:		"m-3,-3 l0,6 6,0 0,-6 z", 
		DIAMOND:	"m0,-3 l3,3 -3,3 -3,-3 z", 
		CROSS:		"m0,-3 l0,6 m-3,-3 l6,0", 
		X:			"m-3,-3 l6,6 m0,-6 l-6,6", 
		TRIANGLE:	"m-3,3 l3,-6 3,6 z", 
		TRIANGLE_INVERTED:"m-3,-3 l3,6 3,-6 z"
	};
	dxc.Theme._def={
		//	all objects are structs used directly in dojox.gfx
		chart:{ 
			stroke:null,
			fill: "white"
		},
		plotarea:{ 
			stroke:null,
			fill: "white"
		},
		//	TODO: label rotation on axis
		axis:{
			stroke:	{ //	the axis itself
				color:"#333",
				width:1
			},
			/*
			line:	{ //	in the future can be used for gridlines
				color:"#ccc",
				width:1,
				style:"Dot",
				cap:"round"
			},
			*/
			majorTick:	{ //	major ticks on axis, and used for major gridlines
				color:"#666",
				width:1, 
				length:6, 
				position:"center"
			},
			minorTick:	{ //	minor ticks on axis, and used for minor gridlines
				color:"#666", 
				width:0.8, 
				length:3, 
				position:"center"
			},	
			font: "normal normal normal 7pt Tahoma", //	labels on axis
			fontColor:"#333"						//	color of labels
		},
		series:{
			outline: {width: 0.1, color: "#ccc"},							//	line or outline
			stroke: {width: 1.5, color: "#333"},							//	line or outline
			fill: "#ccc",												//	fill, if appropriate
			font: "normal normal normal 7pt Tahoma",					//	if there's a label
			fontColor: "#000"											// 	color of labels
		},
		marker:{	//	any markers on a series.
			stroke: {width:1},											//	stroke or outline
			fill: "#333",												//	fill if needed
			font: "normal normal normal 7pt Tahoma",					//	label
			fontColor: "#000"
		},
		colors:[ "#54544c","#858e94","#6e767a","#948585","#474747" ]
	};
	
	//	prototype methods
	dojo.extend(dxc.Theme, {
		defineColors: function(obj){
			//	summary:
			//		Generate a set of colors for the theme based on keyword
			//		arguments
			var kwArgs=obj||{};

			//	note that we've changed the default number from 32 to 4 colors
			//	are cycled anyways.
			var c=[], n=kwArgs.num||5;	//	the number of colors to generate
			if(kwArgs.colors){
				//	we have an array of colors predefined, so fix for the number of series.
				var l=kwArgs.colors.length;
				for(var i=0; i<n; i++){
					c.push(kwArgs.colors[i%l]);
				}
				this.colors=c;
			}else if(kwArgs.hue){
				//	single hue, generate a set based on brightness
				var s=kwArgs.saturation||100;	//	saturation
				var st=kwArgs.low||30;
				var end=kwArgs.high||90;
				//	we'd like it to be a little on the darker side.
				var l=(end+st)/2;

				//	alternately, use "shades"
				this.colors = dojox.color.Palette.generate(
					dojox.color.fromHsv(kwArgs.hue, s, l), "monochromatic"
				).colors;
			}else if(kwArgs.generator){
				//	pass a base color and the name of a generator
				this.colors=dojox.color.Palette.generate(kwArgs.base, kwArgs.generator).colors;
			}
		},
	
		_buildMarkerArray: function(){
			this._markers = [];
			for(var p in this.markers){ this._markers.push(this.markers[p]); }
			//	reset the position
			this._current.marker=0;
		},

		_clone: function(){
			//	summary:
			//		Return a clone of this theme, with the position vars reset to 0.
			return new dxc.Theme({
				chart: this.chart,
				plotarea: this.plotarea,
				axis: this.axis,
				series: this.series,
				marker: this.marker,
				antiAlias: this.antiAlias,
				assignColors: this.assignColors,
				assignMarkers: this.assigneMarkers,
				colors: dojo.delegate(this.colors)
			});
		},

		addMarker:function(/*String*/ name, /*String*/ segment){
			//	summary:
			//		Add a custom marker to this theme.
			//	example:
			//	|	myTheme.addMarker("Ellipse", foo);
			this.markers[name]=segment;
			this._buildMarkerArray();
		},
		setMarkers:function(/*Object*/ obj){
			//	summary:
			//		Set all the markers of this theme at once.  obj should be a
			//		dictionary of keys and path segments.
			//
			//	example:
			//	|	myTheme.setMarkers({ "CIRCLE": foo });
			this.markers=obj;
			this._buildMarkerArray();
		},

		next: function(/*String?*/ type){
			//	summary:
			//		get either the next color or the next marker, depending on
			//		what was passed. If type is not passed, it assumes color.
			//	type:
			//		Optional. One of either "color" or "marker". Defaults to
			//		"color".
			//	example:
			//	|	var color = myTheme.next();
			//	|	var color = myTheme.next("color");
			//	|	var marker = myTheme.next("marker");
			if(type == "marker"){
				return this._markers[ this._current.marker++ % this._markers.length ];
			}else{
				return this.colors[ this._current.color++ % this.colors.length ];
			}
		},
		clear: function(){
			// summary:
			//		resets both marker and color counters back to the start.
			//		Subsequent calls to `next` will retrievie the first value
			//		of each depending on the passed type.
			this._current = {color: 0, marker: 0};
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.Element"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.Element"] = true;
dojo.provide("dojox.charting.Element");

dojo.declare("dojox.charting.Element", null, {
	constructor: function(chart){
		this.chart = chart;
		this.group = null;
		this.htmlElements = [];
		this.dirty = true;
	},
	createGroup: function(creator){
		if(!creator){ creator = this.chart.surface; }
		if(!this.group){
			this.group = creator.createGroup();
		}
		return this;
	},
	purgeGroup: function(){
		this.destroyHtmlElements();
		if(this.group){
			this.group.clear();
			this.group.removeShape();
			this.group = null;
		}
		this.dirty = true;
		return this;
	},
	cleanGroup: function(creator){
		this.destroyHtmlElements();
		if(!creator){ creator = this.chart.surface; }
		if(this.group){
			this.group.clear();
		}else{
			this.group = creator.createGroup();
		}
		this.dirty = true;
		return this;
	},
	destroyHtmlElements: function(){
		if(this.htmlElements.length){
			dojo.forEach(this.htmlElements, dojo.destroy);
			this.htmlElements = [];
		}
	},
	destroy: function(){
		this.purgeGroup();
	}
});

}

if(!dojo._hasResource["dojox.charting.Series"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.Series"] = true;
dojo.provide("dojox.charting.Series");



dojo.declare("dojox.charting.Series", dojox.charting.Element, {
	constructor: function(chart, data, kwArgs){
		dojo.mixin(this, kwArgs);
		if(typeof this.plot != "string"){ this.plot = "default"; }
		this.data = data;
		this.dirty = true;
		this.clear();
	},
	clear: function(){
		this.dyn = {};
	}
});

}

if(!dojo._hasResource["dojox.charting.scaler.common"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.scaler.common"] = true;
dojo.provide("dojox.charting.scaler.common");

(function(){
	var eq = function(/*Number*/ a, /*Number*/ b){
		// summary: compare two FP numbers for equality
		return Math.abs(a - b) <= 1e-6 * (Math.abs(a) + Math.abs(b));	// Boolean
	};
	
	dojo.mixin(dojox.charting.scaler.common, {
		findString: function(/*String*/ val, /*Array*/ text){
			val = val.toLowerCase();
			for(var i = 0; i < text.length; ++i){
				if(val == text[i]){ return true; }
			}
			return false;
		},
		getNumericLabel: function(/*Number*/ number, /*Number*/ precision, /*Object*/ kwArgs){
			var def = kwArgs.fixed ? 
						number.toFixed(precision < 0 ? -precision : 0) : 
						number.toString();
			if(kwArgs.labelFunc){
				var r = kwArgs.labelFunc(def, number, precision);
				if(r){ return r; }
				// else fall through to the regular labels search
			}
			if(kwArgs.labels){
				// classic binary search
				var l = kwArgs.labels, lo = 0, hi = l.length;
				while(lo < hi){
					var mid = Math.floor((lo + hi) / 2), val = l[mid].value;
					if(val < number){
						lo = mid + 1;
					}else{
						hi = mid;
					}
				}
				// lets take into account FP errors
				if(lo < l.length && eq(l[lo].value, number)){
					return l[lo].text;
				}
				--lo;
				if(lo >= 0 && lo < l.length && eq(l[lo].value, number)){
					return l[lo].text;
				}
				lo += 2;
				if(lo < l.length && eq(l[lo].value, number)){
					return l[lo].text;
				}
				// otherwise we will produce a number
			}
			return def;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.scaler.linear"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.scaler.linear"] = true;
dojo.provide("dojox.charting.scaler.linear");


(function(){
	var deltaLimit = 3,	// pixels
		dc = dojox.charting, dcs = dc.scaler, dcsc = dcs.common,
		findString = dcsc.findString,
		getLabel = dcsc.getNumericLabel;
	
	var calcTicks = function(min, max, kwArgs, majorTick, minorTick, microTick, span){
		kwArgs = dojo.delegate(kwArgs);
		if(!majorTick){
			if(kwArgs.fixUpper == "major"){ kwArgs.fixUpper = "minor"; }
			if(kwArgs.fixLower == "major"){ kwArgs.fixLower = "minor"; }
		}
		if(!minorTick){
			if(kwArgs.fixUpper == "minor"){ kwArgs.fixUpper = "micro"; }
			if(kwArgs.fixLower == "minor"){ kwArgs.fixLower = "micro"; }
		}
		if(!microTick){
			if(kwArgs.fixUpper == "micro"){ kwArgs.fixUpper = "none"; }
			if(kwArgs.fixLower == "micro"){ kwArgs.fixLower = "none"; }
		}
		var lowerBound = findString(kwArgs.fixLower, ["major"]) ?
				Math.floor(kwArgs.min / majorTick) * majorTick :
					findString(kwArgs.fixLower, ["minor"]) ?
						Math.floor(kwArgs.min / minorTick) * minorTick :
							findString(kwArgs.fixLower, ["micro"]) ?
								Math.floor(kwArgs.min / microTick) * microTick : kwArgs.min,
			upperBound = findString(kwArgs.fixUpper, ["major"]) ?
				Math.ceil(kwArgs.max / majorTick) * majorTick :
					findString(kwArgs.fixUpper, ["minor"]) ?
						Math.ceil(kwArgs.max / minorTick) * minorTick :
							findString(kwArgs.fixUpper, ["micro"]) ?
								Math.ceil(kwArgs.max / microTick) * microTick : kwArgs.max;
								
		if(kwArgs.useMin){ min = lowerBound; }
		if(kwArgs.useMax){ max = upperBound; }
		
		var majorStart = (!majorTick || kwArgs.useMin && findString(kwArgs.fixLower, ["major"])) ?
				min : Math.ceil(min / majorTick) * majorTick,
			minorStart = (!minorTick || kwArgs.useMin && findString(kwArgs.fixLower, ["major", "minor"])) ?
				min : Math.ceil(min / minorTick) * minorTick,
			microStart = (! microTick || kwArgs.useMin && findString(kwArgs.fixLower, ["major", "minor", "micro"])) ?
				min : Math.ceil(min / microTick) * microTick,
			majorCount = !majorTick ? 0 : (kwArgs.useMax && findString(kwArgs.fixUpper, ["major"]) ?
				Math.round((max - majorStart) / majorTick) :
				Math.floor((max - majorStart) / majorTick)) + 1,
			minorCount = !minorTick ? 0 : (kwArgs.useMax && findString(kwArgs.fixUpper, ["major", "minor"]) ?
				Math.round((max - minorStart) / minorTick) :
				Math.floor((max - minorStart) / minorTick)) + 1,
			microCount = !microTick ? 0 : (kwArgs.useMax && findString(kwArgs.fixUpper, ["major", "minor", "micro"]) ?
				Math.round((max - microStart) / microTick) :
				Math.floor((max - microStart) / microTick)) + 1,
			minorPerMajor  = minorTick ? Math.round(majorTick / minorTick) : 0,
			microPerMinor  = microTick ? Math.round(minorTick / microTick) : 0,
			majorPrecision = majorTick ? Math.floor(Math.log(majorTick) / Math.LN10) : 0,
			minorPrecision = minorTick ? Math.floor(Math.log(minorTick) / Math.LN10) : 0,
			scale = span / (max - min);	
		if(!isFinite(scale)){ scale = 1; }
		
		return {
			bounds: {
				lower:	lowerBound,
				upper:	upperBound,
				from:	min,
				to:		max,
				scale:	scale,
				span:	span
			},
			major: {
				tick:	majorTick,
				start:	majorStart,
				count:	majorCount,
				prec:	majorPrecision
			},
			minor: {
				tick:	minorTick,
				start:	minorStart,
				count:	minorCount,
				prec:	minorPrecision
			},
			micro: {
				tick:	microTick,
				start:	microStart,
				count:	microCount,
				prec:	0
			},
			minorPerMajor:	minorPerMajor,
			microPerMinor:	microPerMinor,
			scaler:			dcs.linear
		};
	};
	
	dojo.mixin(dojox.charting.scaler.linear, {
		buildScaler: function(/*Number*/ min, /*Number*/ max, /*Number*/ span, /*Object*/ kwArgs){
			var h = {fixUpper: "none", fixLower: "none", natural: false};
			if(kwArgs){
				if("fixUpper" in kwArgs){ h.fixUpper = String(kwArgs.fixUpper); }
				if("fixLower" in kwArgs){ h.fixLower = String(kwArgs.fixLower); }
				if("natural"  in kwArgs){ h.natural  = Boolean(kwArgs.natural); }
			}
			
			// update bounds
			if("min" in kwArgs){ min = kwArgs.min; }
			if("max" in kwArgs){ max = kwArgs.max; }
			if(kwArgs.includeZero){
				if(min > 0){ min = 0; }
				if(max < 0){ max = 0; }
			}
			h.min = min;
			h.useMin = true;
			h.max = max;
			h.useMax = true;
			
			if("from" in kwArgs){
				min = kwArgs.from;
				h.useMin = false;
			}
			if("to" in kwArgs){
				max = kwArgs.to;
				h.useMax = false;
			}
			
			// check for erroneous condition
			if(max <= min){
				return calcTicks(min, max, h, 0, 0, 0, span);	// Object
			}
			
			var mag = Math.floor(Math.log(max - min) / Math.LN10),
				major = kwArgs && ("majorTickStep" in kwArgs) ? kwArgs.majorTickStep : Math.pow(10, mag), 
				minor = 0, micro = 0, ticks;
				
			// calculate minor ticks
			if(kwArgs && ("minorTickStep" in kwArgs)){
				minor = kwArgs.minorTickStep;
			}else{
				do{
					minor = major / 10;
					if(!h.natural || minor > 0.9){
						ticks = calcTicks(min, max, h, major, minor, 0, span);
						if(ticks.bounds.scale * ticks.minor.tick > deltaLimit){ break; }
					}
					minor = major / 5;
					if(!h.natural || minor > 0.9){
						ticks = calcTicks(min, max, h, major, minor, 0, span);
						if(ticks.bounds.scale * ticks.minor.tick > deltaLimit){ break; }
					}
					minor = major / 2;
					if(!h.natural || minor > 0.9){
						ticks = calcTicks(min, max, h, major, minor, 0, span);
						if(ticks.bounds.scale * ticks.minor.tick > deltaLimit){ break; }
					}
					return calcTicks(min, max, h, major, 0, 0, span);	// Object
				}while(false);
			}
	
			// calculate micro ticks
			if(kwArgs && ("microTickStep" in kwArgs)){
				micro = kwArgs.microTickStep;
				ticks = calcTicks(min, max, h, major, minor, micro, span);
			}else{
				do{
					micro = minor / 10;
					if(!h.natural || micro > 0.9){
						ticks = calcTicks(min, max, h, major, minor, micro, span);
						if(ticks.bounds.scale * ticks.micro.tick > deltaLimit){ break; }
					}
					micro = minor / 5;
					if(!h.natural || micro > 0.9){
						ticks = calcTicks(min, max, h, major, minor, micro, span);
						if(ticks.bounds.scale * ticks.micro.tick > deltaLimit){ break; }
					}
					micro = minor / 2;
					if(!h.natural || micro > 0.9){
						ticks = calcTicks(min, max, h, major, minor, micro, span);
						if(ticks.bounds.scale * ticks.micro.tick > deltaLimit){ break; }
					}
					micro = 0;
				}while(false);
			}
	
			return micro ? ticks : calcTicks(min, max, h, major, minor, 0, span);	// Object
		},
		buildTicks: function(/*Object*/ scaler, /*Object*/ kwArgs){
			var step, next, tick,
				nextMajor = scaler.major.start, 
				nextMinor = scaler.minor.start, 
				nextMicro = scaler.micro.start;
			if(kwArgs.microTicks && scaler.micro.tick){
				step = scaler.micro.tick, next = nextMicro;
			}else if(kwArgs.minorTicks && scaler.minor.tick){
				step = scaler.minor.tick, next = nextMinor;
			}else if(scaler.major.tick){
				step = scaler.major.tick, next = nextMajor;
			}else{
				// no ticks
				return null;
			}
			// make sure that we have finite bounds
			var revScale = 1 / scaler.bounds.scale;
			if(scaler.bounds.to <= scaler.bounds.from || isNaN(revScale) || !isFinite(revScale) ||
					step <= 0 || isNaN(step) || !isFinite(step)){
				// no ticks
				return null;
			}
			// loop over all ticks
			var majorTicks = [], minorTicks = [], microTicks = [];
			while(next <= scaler.bounds.to + revScale){
				if(Math.abs(nextMajor - next) < step / 2){
					// major tick
					tick = {value: nextMajor};
					if(kwArgs.majorLabels){
						tick.label = getLabel(nextMajor, scaler.major.prec, kwArgs);
					}
					majorTicks.push(tick);
					nextMajor += scaler.major.tick;
					nextMinor += scaler.minor.tick;
					nextMicro += scaler.micro.tick;
				}else if(Math.abs(nextMinor - next) < step / 2){
					// minor tick
					if(kwArgs.minorTicks){
						tick = {value: nextMinor};
						if(kwArgs.minorLabels && (scaler.minMinorStep <= scaler.minor.tick * scaler.bounds.scale)){
							tick.label = getLabel(nextMinor, scaler.minor.prec, kwArgs);
						}
						minorTicks.push(tick);
					}
					nextMinor += scaler.minor.tick;
					nextMicro += scaler.micro.tick;
				}else{
					// micro tick
					if(kwArgs.microTicks){
						microTicks.push({value: nextMicro});
					}
					nextMicro += scaler.micro.tick;
				}
				next += step;
			}
			return {major: majorTicks, minor: minorTicks, micro: microTicks};	// Object
		},
		getTransformerFromModel: function(/*Object*/ scaler){
			var offset = scaler.bounds.from, scale = scaler.bounds.scale;
			return function(x){ return (x - offset) * scale; };	// Function
		},
		getTransformerFromPlot: function(/*Object*/ scaler){
			var offset = scaler.bounds.from, scale = scaler.bounds.scale;
			return function(x){ return x / scale + offset; };	// Function
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.axis2d.common"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.axis2d.common"] = true;
dojo.provide("dojox.charting.axis2d.common");



(function(){
	var g = dojox.gfx;
	
	function clearNode(s){
		s.marginLeft   = "0px";
		s.marginTop    = "0px";
		s.marginRight  = "0px";
		s.marginBottom = "0px";
		s.paddingLeft   = "0px";
		s.paddingTop    = "0px";
		s.paddingRight  = "0px";
		s.paddingBottom = "0px";
		s.borderLeftWidth   = "0px";
		s.borderTopWidth    = "0px";
		s.borderRightWidth  = "0px";
		s.borderBottomWidth = "0px";
	}
	
	dojo.mixin(dojox.charting.axis2d.common, {
		createText: {
			gfx: function(chart, creator, x, y, align, text, font, fontColor){
				return creator.createText({
					x: x, y: y, text: text, align: align
				}).setFont(font).setFill(fontColor);
			},
			html: function(chart, creator, x, y, align, text, font, fontColor){
				// setup the text node
				var p = dojo.doc.createElement("div"), s = p.style;
				clearNode(s);
				s.font = font;
				p.innerHTML = String(text).replace(/\s/g, "&nbsp;");
				s.color = fontColor;
				// measure the size
				s.position = "absolute";
				s.left = "-10000px";
				dojo.body().appendChild(p);
				var size = g.normalizedLength(g.splitFontString(font).size),
					box = dojo.marginBox(p);
				// new settings for the text node
				dojo.body().removeChild(p);
				s.position = "relative";
				switch(align){
					case "middle":
						s.left = Math.floor(x - box.w / 2) + "px";
						break;
					case "end":
						s.left = Math.floor(x - box.w) + "px";
						break;
					//case "start":
					default:
						s.left = Math.floor(x) + "px";
						break;
				}
				s.top = Math.floor(y - size) + "px";
				// setup the wrapper node
				var wrap = dojo.doc.createElement("div"), w = wrap.style;
				clearNode(w);
				w.width = "0px";
				w.height = "0px";
				// insert nodes
				wrap.appendChild(p)
				chart.node.insertBefore(wrap, chart.node.firstChild);
				return wrap;
			}
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.axis2d.Base"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.axis2d.Base"] = true;
dojo.provide("dojox.charting.axis2d.Base");



dojo.declare("dojox.charting.axis2d.Base", dojox.charting.Element, {
	constructor: function(chart, kwArgs){
		this.vertical = kwArgs && kwArgs.vertical;
	},
	clear: function(){
		return this;
	},
	initialized: function(){
		return false;
	},
	calculate: function(min, max, span){
		return this;
	},
	getScaler: function(){
		return null;
	},
	getTicks: function(){
		return null;
	},
	getOffsets: function(){
		return {l: 0, r: 0, t: 0, b: 0};
	},
	render: function(dim, offsets){
		return this;
	}
});

}

if(!dojo._hasResource["dojox.lang.utils"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.utils"] = true;
dojo.provide("dojox.lang.utils");

(function(){
	var empty = {}, du = dojox.lang.utils;

	var clone = function(o){
		if(dojo.isArray(o)){
			return dojo._toArray(o);
		}
		if(!dojo.isObject(o) || dojo.isFunction(o)){
			return o;
		}
		return dojo.delegate(o);
	}
	
	dojo.mixin(du, {
		coerceType: function(target, source){
			switch(typeof target){
				case "number":	return Number(eval("(" + source + ")"));
				case "string":	return String(source);
				case "boolean":	return Boolean(eval("(" + source + ")"));
			}
			return eval("(" + source + ")");
		},
		
		updateWithObject: function(target, source, conv){
			// summary: updates an existing object in place with properties from an "source" object.
			// target: Object: the "target" object to be updated
			// source: Object: the "source" object, whose properties will be used to source the existed object.
			// conv: Boolean?: force conversion to the original type
			if(!source){ return target; }
			for(var x in target){
				if(x in source && !(x in empty)){
					var t = target[x];
					if(t && typeof t == "object"){
						du.updateWithObject(t, source[x], conv);
					}else{
						target[x] = conv ? du.coerceType(t, source[x]) : clone(source[x]);
					}
				}
			}
			return target;	// Object
		},
	
		updateWithPattern: function(target, source, pattern, conv){
			// summary: updates an existing object in place with properties from an "source" object.
			// target: Object: the "target" object to be updated
			// source: Object: the "source" object, whose properties will be used to source the existed object.
			// pattern: Array: an array of properties to be copied
			// conv: Boolean?: force conversion to the original type
			if(!source || !pattern){ return target; }
			for(var x in pattern){
				if(x in source && !(x in empty)){
					target[x] = conv ? du.coerceType(pattern[x], source[x]) : clone(source[x]);
				}
			}
			return target;	// Object
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.axis2d.Default"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.axis2d.Default"] = true;
dojo.provide("dojox.charting.axis2d.Default");











(function(){
	var dc = dojox.charting,
		df = dojox.lang.functional,
		du = dojox.lang.utils,
		g = dojox.gfx,
		lin = dc.scaler.linear,
		labelGap = 4;	// in pixels

	dojo.declare("dojox.charting.axis2d.Default", dojox.charting.axis2d.Base, {
		 defaultParams: {
			vertical:    false,		// true for vertical axis
			fixUpper:    "none",	// align the upper on ticks: "major", "minor", "micro", "none"
			fixLower:    "none",	// align the lower on ticks: "major", "minor", "micro", "none"
			natural:     false,		// all tick marks should be made on natural numbers
			leftBottom:  true,		// position of the axis, used with "vertical"
			includeZero: false,		// 0 should be included
			fixed:       true,		// all labels are fixed numbers
			majorLabels: true,		// draw major labels
			minorTicks:  true,		// draw minor ticks
			minorLabels: true,		// draw minor labels
			microTicks:  false,		// draw micro ticks
			htmlLabels:  true		// use HTML to draw labels
		},
		optionalParams: {
			min:			0,	// minimal value on this axis
			max:			1,	// maximal value on this axis
			from:			0,	// visible from this value
			to:				1,	// visible to this value
			majorTickStep:	4,	// major tick step
			minorTickStep:	2,	// minor tick step
			microTickStep:	1,	// micro tick step
			labels:			[],	// array of labels for major ticks
								// with corresponding numeric values
								// ordered by values
			labelFunc:		null, // function to compute label values
			maxLabelSize:	0,	// size in px. For use with labelFunc

			// TODO: add support for minRange!
			// minRange:		1,	// smallest distance from min allowed on the axis

			// theme components
			stroke:			{},	// stroke for an axis
			majorTick:		{},	// stroke + length for a tick
			minorTick:		{},	// stroke + length for a tick
			microTick:		{},	// stroke + length for a tick
			font:			"",	// font for labels
			fontColor:		""	// color for labels as a string
		},

		constructor: function(chart, kwArgs){
			this.opt = dojo.delegate(this.defaultParams, kwArgs);
			// du.updateWithObject(this.opt, kwArgs);
			du.updateWithPattern(this.opt, kwArgs, this.optionalParams);
		},
		dependOnData: function(){
			return !("min" in this.opt) || !("max" in this.opt);
		},
		clear: function(){
			delete this.scaler;
			delete this.ticks;
			this.dirty = true;
			return this;
		},
		initialized: function(){
			return "scaler" in this && !(this.dirty && this.dependOnData());
		},
		setWindow: function(scale, offset){
			this.scale  = scale;
			this.offset = offset;
			return this.clear();
		},
		getWindowScale: function(){
			return "scale" in this ? this.scale : 1;
		},
		getWindowOffset: function(){
			return "offset" in this ? this.offset : 0;
		},
		calculate: function(min, max, span, labels){
			if(this.initialized()){ return this; }
			this.labels = "labels" in this.opt ? this.opt.labels : labels;
			this.scaler = lin.buildScaler(min, max, span, this.opt);
			if("scale" in this){
				// calculate new range
				this.opt.from = this.scaler.bounds.lower + this.offset;
				this.opt.to   = (this.scaler.bounds.upper - this.scaler.bounds.lower) / this.scale + this.opt.from;
				// make sure that bounds are correct
				if(!isFinite(this.opt.from) || isNaN(this.opt.from) || !isFinite(this.opt.to) || isNaN(this.opt.to) ||
						this.opt.to - this.opt.from >= this.scaler.bounds.upper - this.scaler.bounds.lower){
					// any error --- remove from/to bounds
					delete this.opt.from;
					delete this.opt.to;
					delete this.scale;
					delete this.offset;
				}else{
					// shift the window, if we are out of bounds
					if(this.opt.from < this.scaler.bounds.lower){
						this.opt.to   += this.scaler.bounds.lower - this.opt.from;
						this.opt.from  = this.scaler.bounds.lower;
					}else if(this.opt.to > this.scaler.bounds.upper){
						this.opt.from += this.scaler.bounds.upper - this.opt.to;
						this.opt.to    = this.scaler.bounds.upper;
					}
					// update the offset
					this.offset = this.opt.from - this.scaler.bounds.lower;
				}
				// re-calculate the scaler
				this.scaler = lin.buildScaler(min, max, span, this.opt);
				// cleanup
				if(this.scale == 1 && this.offset == 0){
					delete this.scale;
					delete this.offset;
				}
			}
			var minMinorStep = 0, ta = this.chart.theme.axis,
				taFont = "font" in this.opt ? this.opt.font : ta.font,
				size = taFont ? g.normalizedLength(g.splitFontString(taFont).size) : 0;
			if(this.vertical){
				if(size){
					minMinorStep = size + labelGap;
				}
			}else{
				if(size){
					var labelWidth, i;
					if(this.opt.labelFunc && this.opt.maxLabelSize){
						labelWidth = this.opt.maxLabelSize;
					}else if(this.labels){
						labelWidth = df.foldl(df.map(this.labels, function(label){
							return dojox.gfx._base._getTextBox(label.text, {font: taFont}).w;
						}), "Math.max(a, b)", 0);
					}else{
						var labelLength = Math.ceil(Math.log(Math.max(Math.abs(this.scaler.bounds.from),
								Math.abs(this.scaler.bounds.to))) / Math.LN10),
							t = [];
						if(this.scaler.bounds.from < 0 || this.scaler.bounds.to < 0){ t.push("-"); }
						t.push(dojo.string.rep("9", labelLength));
						var precision = Math.floor(Math.log(this.scaler.bounds.to - this.scaler.bounds.from) / Math.LN10);
						if(precision > 0){
							t.push(".");
							for(i = 0; i < precision; ++i){ t.push("9"); }
						}
						labelWidth = dojox.gfx._base._getTextBox(t.join(""), {font: taFont}).w;
					}
					minMinorStep = labelWidth + labelGap;
				}
			}
			this.scaler.minMinorStep = minMinorStep;
			this.ticks = lin.buildTicks(this.scaler, this.opt);
			return this;
		},
		getScaler: function(){
			return this.scaler;
		},
		getTicks: function(){
			return this.ticks;
		},
		getOffsets: function(){
			var offsets = {l: 0, r: 0, t: 0, b: 0}, labelWidth, a, b, c, d,
				gtb = dojox.gfx._base._getTextBox, gl = dc.scaler.common.getNumericLabel,
				offset = 0, ta = this.chart.theme.axis,
				taFont = "font" in this.opt ? this.opt.font : ta.font,
				taMajorTick = "majorTick" in this.opt ? this.opt.majorTick : ta.majorTick,
				taMinorTick = "minorTick" in this.opt ? this.opt.minorTick : ta.minorTick,
				size = taFont ? g.normalizedLength(g.splitFontString(taFont).size) : 0,
				s = this.scaler;
			if(!s){
				return offsets;
			}
			if(this.vertical){
				if(size){
					if(this.opt.labelFunc && this.opt.maxLabelSize){
						labelWidth = this.opt.maxLabelSize;
					}else if(this.labels){
						labelWidth = df.foldl(df.map(this.labels, function(label){
							return dojox.gfx._base._getTextBox(label.text, {font: taFont}).w;
						}), "Math.max(a, b)", 0);
					}else{
						a = gtb(gl(s.major.start, s.major.prec, this.opt), {font: taFont}).w;
						b = gtb(gl(s.major.start + s.major.count * s.major.tick, s.major.prec, this.opt), {font: taFont}).w;
						c = gtb(gl(s.minor.start, s.minor.prec, this.opt), {font: taFont}).w;
						d = gtb(gl(s.minor.start + s.minor.count * s.minor.tick, s.minor.prec, this.opt), {font: taFont}).w;
						labelWidth = Math.max(a, b, c, d);
					}
					offset = labelWidth + labelGap;
				}
				offset += labelGap + Math.max(taMajorTick.length, taMinorTick.length);
				offsets[this.opt.leftBottom ? "l" : "r"] = offset;
				offsets.t = offsets.b = size / 2;
			}else{
				if(size){
					offset = size + labelGap;
				}
				offset += labelGap + Math.max(taMajorTick.length, taMinorTick.length);
				offsets[this.opt.leftBottom ? "b" : "t"] = offset;
				if(size){
					if(this.opt.labelFunc && this.opt.maxLabelSize){
						labelWidth = this.opt.maxLabelSize;
					}else if(this.labels){
						labelWidth = df.foldl(df.map(this.labels, function(label){
							return dojox.gfx._base._getTextBox(label.text, {font: taFont}).w;
						}), "Math.max(a, b)", 0);
					}else{
						a = gtb(gl(s.major.start, s.major.prec, this.opt), {font: taFont}).w;
						b = gtb(gl(s.major.start + s.major.count * s.major.tick, s.major.prec, this.opt), {font: taFont}).w;
						c = gtb(gl(s.minor.start, s.minor.prec, this.opt), {font: taFont}).w;
						d = gtb(gl(s.minor.start + s.minor.count * s.minor.tick, s.minor.prec, this.opt), {font: taFont}).w;
						labelWidth = Math.max(a, b, c, d);
					}
					offsets.l = offsets.r = labelWidth / 2;
				}
			}
			return offsets;
		},
		render: function(dim, offsets){
			if(!this.dirty){ return this; }
			// prepare variable
			var start, stop, axisVector, tickVector, labelOffset, labelAlign,
				ta = this.chart.theme.axis,
				taStroke = "stroke" in this.opt ? this.opt.stroke : ta.stroke,
				taMajorTick = "majorTick" in this.opt ? this.opt.majorTick : ta.majorTick,
				taMinorTick = "minorTick" in this.opt ? this.opt.minorTick : ta.minorTick,
				taMicroTick = "microTick" in this.opt ? this.opt.microTick : ta.minorTick,
				taFont = "font" in this.opt ? this.opt.font : ta.font,
				taFontColor = "fontColor" in this.opt ? this.opt.fontColor : ta.fontColor,
				tickSize = Math.max(taMajorTick.length, taMinorTick.length),
				size = taFont ? g.normalizedLength(g.splitFontString(taFont).size) : 0;
			if(this.vertical){
				start = {y: dim.height - offsets.b};
				stop  = {y: offsets.t};
				axisVector = {x: 0, y: -1};
				if(this.opt.leftBottom){
					start.x = stop.x = offsets.l;
					tickVector = {x: -1, y: 0};
					labelAlign = "end";
				}else{
					start.x = stop.x = dim.width - offsets.r;
					tickVector = {x: 1, y: 0};
					labelAlign = "start";
				}
				labelOffset = {x: tickVector.x * (tickSize + labelGap), y: size * 0.4};
			}else{
				start = {x: offsets.l};
				stop  = {x: dim.width - offsets.r};
				axisVector = {x: 1, y: 0};
				labelAlign = "middle";
				if(this.opt.leftBottom){
					start.y = stop.y = dim.height - offsets.b;
					tickVector = {x: 0, y: 1};
					labelOffset = {y: tickSize + labelGap + size};
				}else{
					start.y = stop.y = offsets.t;
					tickVector = {x: 0, y: -1};
					labelOffset = {y: -tickSize - labelGap};
				}
				labelOffset.x = 0;
			}

			// render shapes

			this.cleanGroup();

			try{
				var s = this.group, c = this.scaler, t = this.ticks, canLabel,
					f = lin.getTransformerFromModel(this.scaler),
					forceHtmlLabels = dojox.gfx.renderer == "canvas",
					labelType = forceHtmlLabels || this.opt.htmlLabels && !dojo.isIE && !dojo.isOpera ? "html" : "gfx",
					dx = tickVector.x * taMajorTick.length,
					dy = tickVector.y * taMajorTick.length;

				s.createLine({x1: start.x, y1: start.y, x2: stop.x, y2: stop.y}).setStroke(taStroke);

				dojo.forEach(t.major, function(tick){
					var offset = f(tick.value), elem,
						x = start.x + axisVector.x * offset,
						y = start.y + axisVector.y * offset;
						s.createLine({
							x1: x, y1: y,
							x2: x + dx,
							y2: y + dy
						}).setStroke(taMajorTick);
						if(tick.label){
							elem = dc.axis2d.common.createText[labelType]
											(this.chart, s, x + labelOffset.x, y + labelOffset.y, labelAlign,
												tick.label, taFont, taFontColor);
							if(labelType == "html"){ this.htmlElements.push(elem); }
						}
				}, this);

				dx = tickVector.x * taMinorTick.length;
				dy = tickVector.y * taMinorTick.length;
				canLabel = c.minMinorStep <= c.minor.tick * c.bounds.scale;
				dojo.forEach(t.minor, function(tick){
					var offset = f(tick.value), elem,
						x = start.x + axisVector.x * offset,
						y = start.y + axisVector.y * offset;
						s.createLine({
							x1: x, y1: y,
							x2: x + dx,
							y2: y + dy
						}).setStroke(taMinorTick);
						if(canLabel && tick.label){
							elem = dc.axis2d.common.createText[labelType]
											(this.chart, s, x + labelOffset.x, y + labelOffset.y, labelAlign,
												tick.label, taFont, taFontColor);
							if(labelType == "html"){ this.htmlElements.push(elem); }
						}
				}, this);

				dx = tickVector.x * taMicroTick.length;
				dy = tickVector.y * taMicroTick.length;
				dojo.forEach(t.micro, function(tick){
					var offset = f(tick.value), elem,
						x = start.x + axisVector.x * offset,
						y = start.y + axisVector.y * offset;
						s.createLine({
							x1: x, y1: y,
							x2: x + dx,
							y2: y + dy
						}).setStroke(taMicroTick);
				}, this);
			}catch(e){
				// squelch
			}

			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.common"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.common"] = true;
dojo.provide("dojox.charting.plot2d.common");





(function(){
	var df = dojox.lang.functional, dc = dojox.charting.plot2d.common;

	dojo.mixin(dojox.charting.plot2d.common, {
		makeStroke: function(stroke){
			if(!stroke){ return stroke; }
			if(typeof stroke == "string" || stroke instanceof dojo.Color){
				stroke = {color: stroke};
			}
			return dojox.gfx.makeParameters(dojox.gfx.defaultStroke, stroke);
		},
		augmentColor: function(target, color){
			var t = new dojo.Color(target),
				c = new dojo.Color(color);
			c.a = t.a;
			return c;
		},
		augmentStroke: function(stroke, color){
			var s = dc.makeStroke(stroke);
			if(s){
				s.color = dc.augmentColor(s.color, color);
			}
			return s;
		},
		augmentFill: function(fill, color){
			var fc, c = new dojo.Color(color);
			if(typeof fill == "string" || fill instanceof dojo.Color){
				return dc.augmentColor(fill, color);
			}
			return fill;
		},

		defaultStats: {hmin: Number.POSITIVE_INFINITY, hmax: Number.NEGATIVE_INFINITY,
			vmin: Number.POSITIVE_INFINITY, vmax: Number.NEGATIVE_INFINITY},

		collectSimpleStats: function(series){
			var stats = dojo.clone(dc.defaultStats);
			for(var i = 0; i < series.length; ++i){
				var run = series[i];
				if(!run.data.length){ continue; }
				if(typeof run.data[0] == "number"){
					// 1D case
					var old_vmin = stats.vmin, old_vmax = stats.vmax;
					if(!("ymin" in run) || !("ymax" in run)){
						dojo.forEach(run.data, function(val, i){
							var x = i + 1, y = val;
							if(isNaN(y)){ y = 0; }
							stats.hmin = Math.min(stats.hmin, x);
							stats.hmax = Math.max(stats.hmax, x);
							stats.vmin = Math.min(stats.vmin, y);
							stats.vmax = Math.max(stats.vmax, y);
						});
					}
					if("ymin" in run){ stats.vmin = Math.min(old_vmin, run.ymin); }
					if("ymax" in run){ stats.vmax = Math.max(old_vmax, run.ymax); }
				}else{
					// 2D case
					var old_hmin = stats.hmin, old_hmax = stats.hmax,
						old_vmin = stats.vmin, old_vmax = stats.vmax;
					if(!("xmin" in run) || !("xmax" in run) || !("ymin" in run) || !("ymax" in run)){
						dojo.forEach(run.data, function(val, i){
							var x = val.x, y = val.y;
							if(isNaN(x)){ x = 0; }
							if(isNaN(y)){ y = 0; }
							stats.hmin = Math.min(stats.hmin, x);
							stats.hmax = Math.max(stats.hmax, x);
							stats.vmin = Math.min(stats.vmin, y);
							stats.vmax = Math.max(stats.vmax, y);
						});
					}
					if("xmin" in run){ stats.hmin = Math.min(old_hmin, run.xmin); }
					if("xmax" in run){ stats.hmax = Math.max(old_hmax, run.xmax); }
					if("ymin" in run){ stats.vmin = Math.min(old_vmin, run.ymin); }
					if("ymax" in run){ stats.vmax = Math.max(old_vmax, run.ymax); }
				}
			}
			return stats;
		},

		calculateBarSize: function(/* Number */ availableSize, /* Object */ opt, /* Number? */ clusterSize){
			if(!clusterSize){
				clusterSize = 1;
			}
			var gap = opt.gap, size = (availableSize - 2 * gap) / clusterSize;
			if("minBarSize" in opt){
				size = Math.max(size, opt.minBarSize);
			}
			if("maxBarSize" in opt){
				size = Math.min(size, opt.maxBarSize);
			}
			size = Math.max(size, 1);
			gap = (availableSize - size * clusterSize) / 2;
			return {size: size, gap: gap};	// Object
		},

		collectStackedStats: function(series){
			// collect statistics
			var stats = dojo.clone(dc.defaultStats);
			if(series.length){
				// 1st pass: find the maximal length of runs
				stats.hmin = Math.min(stats.hmin, 1);
				stats.hmax = df.foldl(series, "seed, run -> Math.max(seed, run.data.length)", stats.hmax);
				// 2nd pass: stack values
				for(var i = 0; i < stats.hmax; ++i){
					var v = series[0].data[i];
					if(isNaN(v)){ v = 0; }
					stats.vmin = Math.min(stats.vmin, v);
					for(var j = 1; j < series.length; ++j){
						var t = series[j].data[i];
						if(isNaN(t)){ t = 0; }
						v += t;
					}
					stats.vmax = Math.max(stats.vmax, v);
				}
			}
			return stats;
		},

		curve: function(/* Number[] */a, /* Number|String */tension){
			//	FIX for #7235, submitted by Enzo Michelangeli.
			//	Emulates the smoothing algorithms used in a famous, unnamed spreadsheet
			//		program ;)
			var arr = a.slice(0);
			if(tension == "x") {
				arr[arr.length] = arr[0];   // add a last element equal to the first, closing the loop
			}
			var p=dojo.map(arr, function(item, i){
				if(i==0){ return "M" + item.x + "," + item.y; }
				if(!isNaN(tension)) { // use standard Dojo smoothing in tension is numeric
					var dx=item.x-arr[i-1].x, dy=arr[i-1].y;
					return "C"+(item.x-(tension-1)*(dx/tension))+","+dy+" "+(item.x-(dx/tension))+","+item.y+" "+item.x+","+item.y;
				} else if(tension == "X" || tension == "x" || tension == "S") {
					// use Excel "line smoothing" algorithm (http://xlrotor.com/resources/files.shtml)
					var p0, p1 = arr[i-1], p2 = arr[i], p3;
					var bz1x, bz1y, bz2x, bz2y;
					var f = 1/6;
					if(i==1) {
						if(tension == "x") {
							p0 = arr[arr.length-2];
						} else { // "tension == X || tension == "S"
							p0 = p1;
						}
						f = 1/3;
					} else {
						p0 = arr[i-2];
					}
					if(i==(arr.length-1)) {
						if(tension == "x") {
							p3 = arr[1];
						} else { // "tension == X || tension == "S"
							p3 = p2;
						}
						f = 1/3;
					} else {
						p3 = arr[i+1];
					}
					var p1p2 = Math.sqrt((p2.x-p1.x)*(p2.x-p1.x)+(p2.y-p1.y)*(p2.y-p1.y));
					var p0p2 = Math.sqrt((p2.x-p0.x)*(p2.x-p0.x)+(p2.y-p0.y)*(p2.y-p0.y));
					var p1p3 = Math.sqrt((p3.x-p1.x)*(p3.x-p1.x)+(p3.y-p1.y)*(p3.y-p1.y));

					var p0p2f = p0p2 * f;
					var p1p3f = p1p3 * f;

					if(p0p2f > p1p2/2 && p1p3f > p1p2/2) {
						p0p2f = p1p2/2;
						p1p3f = p1p2/2;
					} else if(p0p2f > p1p2/2) {
						p0p2f = p1p2/2;
						p1p3f = p1p2/2 * p1p3/p0p2;
					} else if(p1p3f > p1p2/2) {
						p1p3f = p1p2/2;
						p0p2f = p1p2/2 * p0p2/p1p3;
					}

					if(tension == "S") {
						if(p0 == p1) { p0p2f = 0; }
						if(p2 == p3) { p1p3f = 0; }
					}

					bz1x = p1.x + p0p2f*(p2.x - p0.x)/p0p2;
					bz1y = p1.y + p0p2f*(p2.y - p0.y)/p0p2;
					bz2x = p2.x - p1p3f*(p3.x - p1.x)/p1p3;
					bz2y = p2.y - p1p3f*(p3.y - p1.y)/p1p3;
				}
				return "C"+(bz1x+","+bz1y+" "+bz2x+","+bz2y+" "+p2.x+","+p2.y);
			});
			return p.join(" ");
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.scaler.primitive"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.scaler.primitive"] = true;
dojo.provide("dojox.charting.scaler.primitive");

dojox.charting.scaler.primitive = {
	buildScaler: function(/*Number*/ min, /*Number*/ max, /*Number*/ span, /*Object*/ kwArgs){
		return {
			bounds: {
				lower: min,
				upper: max,
				from:  min,
				to:    max,
				scale: span / (max - min),
				span:  span
			},
			scaler: dojox.charting.scaler.primitive
		};
	},
	buildTicks: function(/*Object*/ scaler, /*Object*/ kwArgs){
		return {major: [], minor: [], micro: []};	// Object
	},
	getTransformerFromModel: function(/*Object*/ scaler){
		var offset = scaler.bounds.from, scale = scaler.bounds.scale;
		return function(x){ return (x - offset) * scale; };	// Function
	},
	getTransformerFromPlot: function(/*Object*/ scaler){
		var offset = scaler.bounds.from, scale = scaler.bounds.scale;
		return function(x){ return x / scale + offset; };	// Function
	}
};

}

if(!dojo._hasResource["dojox.charting.plot2d.Base"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Base"] = true;
dojo.provide("dojox.charting.plot2d.Base");





dojo.declare("dojox.charting.plot2d.Base", dojox.charting.Element, {
	destroy: function(){
		this.resetEvents();
		this.inherited(arguments);
	},
	clear: function(){
		this.series = [];
		this._hAxis = null;
		this._vAxis = null;
		this.dirty = true;
		return this;
	},
	setAxis: function(axis){
		if(axis){
			this[axis.vertical ? "_vAxis" : "_hAxis"] = axis;
		}
		return this;
	},
	addSeries: function(run){
		this.series.push(run);
		return this;
	},
	calculateAxes: function(dim){
		return this;
	},
	isDirty: function(){
		return this.dirty || this._hAxis && this._hAxis.dirty || this._vAxis && this._vAxis.dirty;
	},
	render: function(dim, offsets){
		return this;
	},
	getRequiredColors: function(){
		return this.series.length;
	},

	// events
	plotEvent: function(o){
		// intentionally empty --- used for events
	},
	connect: function(object, method){
		this.dirty = true;
		return dojo.connect(this, "plotEvent", object, method);
	},
	events: function(){
		var ls = this.plotEvent._listeners;
		if(!ls || !ls.length){ return false; }
		for(var i in ls){
			if(!(i in Array.prototype)){
				return true;
			}
		}
		return false;
	},
	resetEvents: function(){
		this.plotEvent({type: "onplotreset", plot: this});
	},

	// utilities
	_calc: function(dim, stats){
		// calculate scaler
		if(this._hAxis){
			if(!this._hAxis.initialized()){
				this._hAxis.calculate(stats.hmin, stats.hmax, dim.width);
			}
			this._hScaler = this._hAxis.getScaler();
		}else{
			this._hScaler = dojox.charting.scaler.primitive.buildScaler(stats.hmin, stats.hmax, dim.width);
		}
		if(this._vAxis){
			if(!this._vAxis.initialized()){
				this._vAxis.calculate(stats.vmin, stats.vmax, dim.height);
			}
			this._vScaler = this._vAxis.getScaler();
		}else{
			this._vScaler = dojox.charting.scaler.primitive.buildScaler(stats.vmin, stats.vmax, dim.height);
		}
	},

	_connectEvents: function(shape, o){
		shape.connect("onmouseover", this, function(e){
			o.type  = "onmouseover";
			o.event = e;
			this.plotEvent(o);
		});
		shape.connect("onmouseout", this, function(e){
			o.type  = "onmouseout";
			o.event = e;
			this.plotEvent(o);
		});
		shape.connect("onclick", this, function(e){
			o.type  = "onclick";
			o.event = e;
			this.plotEvent(o);
		});
	}
});

}

if(!dojo._hasResource["dojox.charting.plot2d.Default"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Default"] = true;
dojo.provide("dojox.charting.plot2d.Default");








(function(){
	var df = dojox.lang.functional, du = dojox.lang.utils,
		dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.Default", dojox.charting.plot2d.Base, {
		defaultParams: {
			hAxis: "x",		// use a horizontal axis named "x"
			vAxis: "y",		// use a vertical axis named "y"
			lines:   true,	// draw lines
			areas:   false,	// draw areas
			markers: false,	// draw markers
			shadows: 0,		// draw shadows
			tension: 0		// draw curved lines (tension>0)
		},
		optionalParams: {},	// no optional parameters
		
		constructor: function(chart, kwArgs){
			this.opt = dojo.clone(this.defaultParams);
			du.updateWithObject(this.opt, kwArgs);
			this.series = [];
			this.hAxis = this.opt.hAxis;
			this.vAxis = this.opt.vAxis;
		},
		
		calculateAxes: function(dim){
			this._calc(dim, dc.collectSimpleStats(this.series));
			return this;
		},
		render: function(dim, offsets){
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}
			var t = this.chart.theme, stroke, outline, color, marker, events = this.events();
			this.resetEvents();
			for(var i = this.series.length - 1; i >= 0; --i){
				var run = this.series[i];
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				if(!run.data.length){
					run.dirty = false;
					continue;
				}

				var s = run.group, lpoly, 
					ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
					vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler);
				if(typeof run.data[0] == "number"){
					lpoly = dojo.map(run.data, function(v, i){
						return {
							x: ht(i + 1) + offsets.l,
							y: dim.height - offsets.b - vt(v)
						};
					}, this);
				}else{
					lpoly = dojo.map(run.data, function(v, i){
						return {
							x: ht(v.x) + offsets.l,
							y: dim.height - offsets.b - vt(v.y)
						};
					}, this);
				}
				if(!run.fill || !run.stroke){
					// need autogenerated color
					color = run.dyn.color = new dojo.Color(t.next("color"));
				}

				var lpath = this.opt.tension ? dc.curve(lpoly, this.opt.tension) : "";

				if(this.opt.areas){
					var fill = run.fill ? run.fill : dc.augmentFill(t.series.fill, color);
					var apoly = dojo.clone(lpoly);
					if(this.opt.tension){
						var apath = "L" + apoly[apoly.length-1].x + "," + (dim.height - offsets.b) +
							" L" + apoly[0].x + "," + (dim.height - offsets.b) +
							" L" + apoly[0].x + "," + apoly[0].y;
						run.dyn.fill = s.createPath(lpath + " " + apath).setFill(fill).getFill();
					} else {
						apoly.push({x: lpoly[lpoly.length - 1].x, y: dim.height - offsets.b});
						apoly.push({x: lpoly[0].x, y: dim.height - offsets.b});
						apoly.push(lpoly[0]);
						run.dyn.fill = s.createPolyline(apoly).setFill(fill).getFill();
					}
				}
				if(this.opt.lines || this.opt.markers){
					// need a stroke
					stroke = run.stroke ? dc.makeStroke(run.stroke) : dc.augmentStroke(t.series.stroke, color);
					if(run.outline || t.series.outline){
						outline = dc.makeStroke(run.outline ? run.outline : t.series.outline);
						outline.width = 2 * outline.width + stroke.width;
					}
				}
				if(this.opt.markers){
					// need a marker
					marker = run.dyn.marker = run.marker ? run.marker : t.next("marker");
				}
				var frontMarkers = null, outlineMarkers = null, shadowMarkers = null;
				if(this.opt.shadows && stroke){
					var sh = this.opt.shadows, shadowColor = new dojo.Color([0, 0, 0, 0.3]),
						spoly = dojo.map(lpoly, function(c){
							return {x: c.x + sh.dx, y: c.y + sh.dy};
						}),
						shadowStroke = dojo.clone(outline ? outline : stroke);
					shadowStroke.color = shadowColor;
					shadowStroke.width += sh.dw ? sh.dw : 0;
					if(this.opt.lines){
						if(this.opt.tension){
							run.dyn.shadow = s.createPath(dc.curve(spoly, this.opt.tension)).setStroke(shadowStroke).getStroke();
						} else {
							run.dyn.shadow = s.createPolyline(spoly).setStroke(shadowStroke).getStroke();
						}
					}
					if(this.opt.markers){
						shadowMarkers = dojo.map(spoly, function(c){
							return s.createPath("M" + c.x + " " + c.y + " " + marker).
								setStroke(shadowStroke).setFill(shadowColor);
						}, this);
					}
				}
				if(this.opt.lines){
					if(outline){
						if(this.opt.tension){
							run.dyn.outline = s.createPath(lpath).setStroke(outline).getStroke();
						} else {
							run.dyn.outline = s.createPolyline(lpoly).setStroke(outline).getStroke();
						}
					}
					if(this.opt.tension){
						run.dyn.stroke = s.createPath(lpath).setStroke(stroke).getStroke();
					} else {
						run.dyn.stroke = s.createPolyline(lpoly).setStroke(stroke).getStroke();
					}
				}
				if(this.opt.markers){
					frontMarkers = new Array(lpoly.length);
					outlineMarkers = new Array(lpoly.length);
					dojo.forEach(lpoly, function(c, i){
						var path = "M" + c.x + " " + c.y + " " + marker;
						if(outline){
							outlineMarkers[i] = s.createPath(path).setStroke(outline);
						}
						frontMarkers[i] = s.createPath(path).setStroke(stroke).setFill(stroke.color);
					}, this);
					if(events){
						dojo.forEach(frontMarkers, function(s, i){
							var o = {
								element: "marker",
								index:   i,
								run:     run,
								plot:    this,
								hAxis:   this.hAxis || null,
								vAxis:   this.vAxis || null,
								shape:   s,
								outline: outlineMarkers[i] || null,
								shadow:  shadowMarkers && shadowMarkers[i] || null,
								cx:      lpoly[i].x,
								cy:      lpoly[i].y
							};
							if(typeof run.data[0] == "number"){
								o.x = i + 1;
								o.y = run.data[i];
							}else{
								o.x = run.data[i].x;
								o.y = run.data[i].y;
							}
							this._connectEvents(s, o);
						}, this);
					}
				}
				run.dirty = false;
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.Lines"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Lines"] = true;
dojo.provide("dojox.charting.plot2d.Lines");



dojo.declare("dojox.charting.plot2d.Lines", dojox.charting.plot2d.Default, {
	constructor: function(){
		this.opt.lines = true;
	}
});

}

if(!dojo._hasResource["dojox.charting.plot2d.Areas"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Areas"] = true;
dojo.provide("dojox.charting.plot2d.Areas");



dojo.declare("dojox.charting.plot2d.Areas", dojox.charting.plot2d.Default, {
	constructor: function(){
		this.opt.lines = true;
		this.opt.areas = true;
	}
});

}

if(!dojo._hasResource["dojox.charting.plot2d.Markers"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Markers"] = true;
dojo.provide("dojox.charting.plot2d.Markers");



dojo.declare("dojox.charting.plot2d.Markers", dojox.charting.plot2d.Default, {
	constructor: function(){
		this.opt.markers = true;
	}
});

}

if(!dojo._hasResource["dojox.charting.plot2d.MarkersOnly"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.MarkersOnly"] = true;
dojo.provide("dojox.charting.plot2d.MarkersOnly");



dojo.declare("dojox.charting.plot2d.MarkersOnly", dojox.charting.plot2d.Default, {
	constructor: function(){
		this.opt.lines   = false;
		this.opt.markers = true;
	}
});

}

if(!dojo._hasResource["dojox.charting.plot2d.Scatter"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Scatter"] = true;
dojo.provide("dojox.charting.plot2d.Scatter");



dojo.declare("dojox.charting.plot2d.Scatter", dojox.charting.plot2d.Default, {
	constructor: function(){
		this.opt.lines   = false;
		this.opt.markers = true;
	}
});

}

if(!dojo._hasResource["dojox.lang.functional.sequence"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.functional.sequence"] = true;
dojo.provide("dojox.lang.functional.sequence");



// This module adds high-level functions and related constructs:
//	- sequence generators

// If you want more general sequence builders check out listcomp.js and
// unfold() (in fold.js).

// Defined methods:
//	- take any valid lambda argument as the functional argument

(function(){
	var d = dojo, df = dojox.lang.functional;

	d.mixin(df, {
		// sequence generators
		repeat: function(/*Number*/ n, /*Function|String|Array*/ f, /*Object*/ z, /*Object?*/ o){
			// summary: builds an array by repeatedly applying a unary function N times
			//	with a seed value Z. N should be greater than 0.
			o = o || d.global; f = df.lambda(f);
			var t = new Array(n), i = 1;
			t[0] = z;
			for(; i < n; t[i] = z = f.call(o, z), ++i);
			return t;	// Array
		},
		until: function(/*Function|String|Array*/ pr, /*Function|String|Array*/ f, /*Object*/ z, /*Object?*/ o){
			// summary: builds an array by repeatedly applying a unary function with
			//	a seed value Z until the predicate is satisfied.
			o = o || d.global; f = df.lambda(f); pr = df.lambda(pr);
			var t = [];
			for(; !pr.call(o, z); t.push(z), z = f.call(o, z));
			return t;	// Array
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.Stacked"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Stacked"] = true;
dojo.provide("dojox.charting.plot2d.Stacked");








(function(){
	var df = dojox.lang.functional, dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.Stacked", dojox.charting.plot2d.Default, {
		calculateAxes: function(dim){
			var stats = dc.collectStackedStats(this.series);
			this._maxRunLength = stats.hmax;
			this._calc(dim, stats);
			return this;
		},
		render: function(dim, offsets){
			if(this._maxRunLength <= 0){
				return this;
			}

			// stack all values
			var acc = df.repeat(this._maxRunLength, "-> 0", 0);
			for(var i = 0; i < this.series.length; ++i){
				var run = this.series[i];
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j];
					if(isNaN(v)){ v = 0; }
					acc[j] += v;
				}
			}
			// draw runs in backwards
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}

			var t = this.chart.theme, stroke, outline, color, marker, events = this.events(),
				ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
				vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler);
			this.resetEvents();

			for(var i = this.series.length - 1; i >= 0; --i){
				var run = this.series[i];
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				var s = run.group,
					lpoly = dojo.map(acc, function(v, i){
						return {
							x: ht(i + 1) + offsets.l,
							y: dim.height - offsets.b - vt(v)
						};
					}, this);
				if(!run.fill || !run.stroke){
					// need autogenerated color
					color = new dojo.Color(t.next("color"));
				}

				var lpath = this.opt.tension ? dc.curve(lpoly, this.opt.tension) : "";
				
				if(this.opt.areas){
					var apoly = dojo.clone(lpoly);
					var fill = run.fill ? run.fill : dc.augmentFill(t.series.fill, color);
					if(this.opt.tension){
						var p=dc.curve(apoly, this.opt.tension);
						p += " L" + lpoly[lpoly.length - 1].x + "," + (dim.height - offsets.b) +
							" L" + lpoly[0].x + "," + (dim.height - offsets.b) +
							" L" + lpoly[0].x + "," + lpoly[0].y;
						run.dyn.fill = s.createPath(p).setFill(fill).getFill();
					} else {
						apoly.push({x: lpoly[lpoly.length - 1].x, y: dim.height - offsets.b});
						apoly.push({x: lpoly[0].x, y: dim.height - offsets.b});
						apoly.push(lpoly[0]);
						run.dyn.fill = s.createPolyline(apoly).setFill(fill).getFill();
					}
				}
				if(this.opt.lines || this.opt.markers){
					// need a stroke
					stroke = run.stroke ? dc.makeStroke(run.stroke) : dc.augmentStroke(t.series.stroke, color);
					if(run.outline || t.series.outline){
						outline = dc.makeStroke(run.outline ? run.outline : t.series.outline);
						outline.width = 2 * outline.width + stroke.width;
					}
				}
				if(this.opt.markers){
					// need a marker
					marker = run.dyn.marker = run.marker ? run.marker : t.next("marker");
				}
				var frontMarkers, outlineMarkers, shadowMarkers;
				if(this.opt.shadows && stroke){
					var sh = this.opt.shadows, shadowColor = new dojo.Color([0, 0, 0, 0.3]),
						spoly = dojo.map(lpoly, function(c){
							return {x: c.x + sh.dx, y: c.y + sh.dy};
						}),
						shadowStroke = dojo.clone(outline ? outline : stroke);
					shadowStroke.color = shadowColor;
					shadowStroke.width += sh.dw ? sh.dw : 0;
					if(this.opt.lines){
						if(this.opt.tension){
							run.dyn.shadow = s.createPath(dc.curve(spoly, this.opt.tension)).setStroke(shadowStroke).getStroke();
						} else {
							run.dyn.shadow = s.createPolyline(spoly).setStroke(shadowStroke).getStroke();
						}
					}
					if(this.opt.markers){
						shadowMarkers = dojo.map(spoly, function(c){
							return s.createPath("M" + c.x + " " + c.y + " " + marker).
								setStroke(shadowStroke).setFill(shadowColor);
						}, this);
					}
				}
				if(this.opt.lines){
					if(outline){
						if(this.opt.tension){
							run.dyn.outline = s.createPath(lpath).setStroke(outline).getStroke();
						} else {
							run.dyn.outline = s.createPolyline(lpoly).setStroke(outline).getStroke();
						}
					}
					if(this.opt.tension){
						run.dyn.stroke = s.createPath(lpath).setStroke(stroke).getStroke();
					} else {
						run.dyn.stroke = s.createPolyline(lpoly).setStroke(stroke).getStroke();
					}
				}
				if(this.opt.markers){
					frontMarkers = new Array(lpoly.length);
					outlineMarkers = new Array(lpoly.length);
					dojo.forEach(lpoly, function(c, i){
						var path = "M" + c.x + " " + c.y + " " + marker;
						if(outline){
							outlineMarkers[i] = s.createPath(path).setStroke(outline);
						}
						frontMarkers[i] = s.createPath(path).setStroke(stroke).setFill(stroke.color);
					}, this);
					if(events){
						dojo.forEach(frontMarkers, function(s, i){
							var o = {
								element: "marker",
								index:   i,
								run:     run,
								plot:    this,
								hAxis:   this.hAxis || null,
								vAxis:   this.vAxis || null,
								shape:   s,
								outline: outlineMarkers[i] || null,
								shadow:  shadowMarkers && shadowMarkers[i] || null,
								cx:      lpoly[i].x,
								cy:      lpoly[i].y,
								x:       i + 1,
								y:       run.data[i]
							};
							this._connectEvents(s, o);
						}, this);
					}
				}
				run.dirty = false;
				// update the accumulator
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j];
					if(isNaN(v)){ v = 0; }
					acc[j] -= v;
				}
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.StackedLines"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.StackedLines"] = true;
dojo.provide("dojox.charting.plot2d.StackedLines");



dojo.declare("dojox.charting.plot2d.StackedLines", dojox.charting.plot2d.Stacked, {
	constructor: function(){
		this.opt.lines = true;
	}
});

}

if(!dojo._hasResource["dojox.charting.plot2d.StackedAreas"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.StackedAreas"] = true;
dojo.provide("dojox.charting.plot2d.StackedAreas");



dojo.declare("dojox.charting.plot2d.StackedAreas", dojox.charting.plot2d.Stacked, {
	constructor: function(){
		this.opt.lines = true;
		this.opt.areas = true;
	}
});

}

if(!dojo._hasResource["dojox.charting.plot2d.Columns"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Columns"] = true;
dojo.provide("dojox.charting.plot2d.Columns");








(function(){
	var df = dojox.lang.functional, du = dojox.lang.utils,
		dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.Columns", dojox.charting.plot2d.Base, {
		defaultParams: {
			hAxis: "x",		// use a horizontal axis named "x"
			vAxis: "y",		// use a vertical axis named "y"
			gap:	0,		// gap between columns in pixels
			shadows: null	// draw shadows
		},
		optionalParams: {
			minBarSize: 1,	// minimal bar size in pixels
			maxBarSize: 1	// maximal bar size in pixels
		},

		constructor: function(chart, kwArgs){
			this.opt = dojo.clone(this.defaultParams);
			du.updateWithObject(this.opt, kwArgs);
			du.updateWithPattern(this.opt, kwArgs, this.optionalParams);
			this.series = [];
			this.hAxis = this.opt.hAxis;
			this.vAxis = this.opt.vAxis;
		},

		calculateAxes: function(dim){
			var stats = dc.collectSimpleStats(this.series);
			stats.hmin -= 0.5;
			stats.hmax += 0.5;
			this._calc(dim, stats);
			return this;
		},
		render: function(dim, offsets){
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}
			var t = this.chart.theme, color, stroke, fill, f, gap, width,
				ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
				vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler),
				baseline = Math.max(0, this._vScaler.bounds.lower),
				baselineHeight = vt(baseline),
				events = this.events();
			f = dc.calculateBarSize(this._hScaler.bounds.scale, this.opt);
			gap = f.gap;
			width = f.size;
			this.resetEvents();
			for(var i = this.series.length - 1; i >= 0; --i){
				var run = this.series[i];
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				var s = run.group;
				if(!run.fill || !run.stroke){
					// need autogenerated color
					color = run.dyn.color = new dojo.Color(t.next("color"));
				}
				stroke = run.stroke ? run.stroke : dc.augmentStroke(t.series.stroke, color);
				fill = run.fill ? run.fill : dc.augmentFill(t.series.fill, color);
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j],
						vv = vt(v),
						height = vv - baselineHeight,
						h = Math.abs(height);
					if(width >= 1 && h >= 1){
						var rect = {
								x: offsets.l + ht(j + 0.5) + gap,
								y: dim.height - offsets.b - (v > baseline ? vv : baselineHeight),
								width: width, height: h
							},
							shape = s.createRect(rect).setFill(fill).setStroke(stroke);
						run.dyn.fill   = shape.getFill();
						run.dyn.stroke = shape.getStroke();
						if(events){
							var o = {
								element: "column",
								index:   j,
								run:     run,
								plot:    this,
								hAxis:   this.hAxis || null,
								vAxis:   this.vAxis || null,
								shape:   shape,
								x:       j + 0.5,
								y:       v
							};
							this._connectEvents(shape, o);
						}
					}
				}
				run.dirty = false;
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.StackedColumns"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.StackedColumns"] = true;
dojo.provide("dojox.charting.plot2d.StackedColumns");







(function(){
	var df = dojox.lang.functional, dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.StackedColumns", dojox.charting.plot2d.Columns, {
		calculateAxes: function(dim){
			var stats = dc.collectStackedStats(this.series);
			this._maxRunLength = stats.hmax;
			stats.hmin -= 0.5;
			stats.hmax += 0.5;
			this._calc(dim, stats);
			return this;
		},
		render: function(dim, offsets){
			if(this._maxRunLength <= 0){
				return this;
			}

			// stack all values
			var acc = df.repeat(this._maxRunLength, "-> 0", 0);
			for(var i = 0; i < this.series.length; ++i){
				var run = this.series[i];
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j];
					if(isNaN(v)){ v = 0; }
					acc[j] += v;
				}
			}
			// draw runs in backwards
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}
			var t = this.chart.theme, color, stroke, fill, f, gap, width,
				ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
				vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler),
				events = this.events();
			f = dc.calculateBarSize(this._hScaler.bounds.scale, this.opt);
			gap = f.gap;
			width = f.size;
			this.resetEvents();
			for(var i = this.series.length - 1; i >= 0; --i){
				var run = this.series[i];
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				var s = run.group;
				if(!run.fill || !run.stroke){
					// need autogenerated color
					color = run.dyn.color = new dojo.Color(t.next("color"));
				}
				stroke = run.stroke ? run.stroke : dc.augmentStroke(t.series.stroke, color);
				fill = run.fill ? run.fill : dc.augmentFill(t.series.fill, color);
				for(var j = 0; j < acc.length; ++j){
					var v = acc[j],
						height = vt(v);
					if(width >= 1 && height >= 1){
						var shape = s.createRect({
							x: offsets.l + ht(j + 0.5) + gap,
							y: dim.height - offsets.b - vt(v),
							width: width, height: height
						}).setFill(fill).setStroke(stroke);
						run.dyn.fill   = shape.getFill();
						run.dyn.stroke = shape.getStroke();
						if(events){
							var o = {
								element: "column",
								index:   j,
								run:     run,
								plot:    this,
								hAxis:   this.hAxis || null,
								vAxis:   this.vAxis || null,
								shape:   shape,
								x:       j + 0.5,
								y:       v
							};
							this._connectEvents(shape, o);
						}
					}
				}
				run.dirty = false;
				// update the accumulator
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j];
					if(isNaN(v)){ v = 0; }
					acc[j] -= v;
				}
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.ClusteredColumns"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.ClusteredColumns"] = true;
dojo.provide("dojox.charting.plot2d.ClusteredColumns");







(function(){
	var df = dojox.lang.functional, dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.ClusteredColumns", dojox.charting.plot2d.Columns, {
		render: function(dim, offsets){
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}
			var t = this.chart.theme, color, stroke, fill, f, gap, width, thickness,
				ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
				vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler),
				baseline = Math.max(0, this._vScaler.bounds.lower),
				baselineHeight = vt(baseline),
				events = this.events();
			f = dc.calculateBarSize(this._hScaler.bounds.scale, this.opt, this.series.length);
			gap = f.gap;
			width = thickness = f.size;
			this.resetEvents();
			for(var i = 0; i < this.series.length; ++i){
				var run = this.series[i], shift = thickness * i;
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				var s = run.group;
				if(!run.fill || !run.stroke){
					// need autogenerated color
					color = run.dyn.color = new dojo.Color(t.next("color"));
				}
				stroke = run.stroke ? run.stroke : dc.augmentStroke(t.series.stroke, color);
				fill = run.fill ? run.fill : dc.augmentFill(t.series.fill, color);
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j],
						vv = vt(v),
						height = vv - baselineHeight,
						h = Math.abs(height);
					if(width >= 1 && h >= 1){
						var shape = s.createRect({
							x: offsets.l + ht(j + 0.5) + gap + shift,
							y: dim.height - offsets.b - (v > baseline ? vv : baselineHeight),
							width: width, height: h
						}).setFill(fill).setStroke(stroke);
						run.dyn.fill   = shape.getFill();
						run.dyn.stroke = shape.getStroke();
						if(events){
							var o = {
								element: "column",
								index:   j,
								run:     run,
								plot:    this,
								hAxis:   this.hAxis || null,
								vAxis:   this.vAxis || null,
								shape:   shape,
								x:       j + 0.5,
								y:       v
							};
							this._connectEvents(shape, o);
						}
					}
				}
				run.dirty = false;
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.Bars"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Bars"] = true;
dojo.provide("dojox.charting.plot2d.Bars");








(function(){
	var df = dojox.lang.functional, du = dojox.lang.utils,
		dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.Bars", dojox.charting.plot2d.Base, {
		defaultParams: {
			hAxis: "x",		// use a horizontal axis named "x"
			vAxis: "y",		// use a vertical axis named "y"
			gap:	0,		// gap between columns in pixels
			shadows: null	// draw shadows
		},
		optionalParams: {
			minBarSize: 1,	// minimal bar size in pixels
			maxBarSize: 1	// maximal bar size in pixels
		},

		constructor: function(chart, kwArgs){
			this.opt = dojo.clone(this.defaultParams);
			du.updateWithObject(this.opt, kwArgs);
			du.updateWithPattern(this.opt, kwArgs, this.optionalParams);
			this.series = [];
			this.hAxis = this.opt.hAxis;
			this.vAxis = this.opt.vAxis;
		},

		calculateAxes: function(dim){
			var stats = dc.collectSimpleStats(this.series), t;
			stats.hmin -= 0.5;
			stats.hmax += 0.5;
			t = stats.hmin, stats.hmin = stats.vmin, stats.vmin = t;
			t = stats.hmax, stats.hmax = stats.vmax, stats.vmax = t;
			this._calc(dim, stats);
			return this;
		},
		render: function(dim, offsets){
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}
			var t = this.chart.theme, color, stroke, fill, f, gap, height,
				ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
				vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler),
				baseline = Math.max(0, this._hScaler.bounds.lower),
				baselineWidth = ht(baseline),
				events = this.events();
			f = dc.calculateBarSize(this._vScaler.bounds.scale, this.opt);
			gap = f.gap;
			height = f.size;
			this.resetEvents();
			for(var i = this.series.length - 1; i >= 0; --i){
				var run = this.series[i];
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				var s = run.group;
				if(!run.fill || !run.stroke){
					// need autogenerated color
					color = run.dyn.color = new dojo.Color(t.next("color"));
				}
				stroke = run.stroke ? run.stroke : dc.augmentStroke(t.series.stroke, color);
				fill = run.fill ? run.fill : dc.augmentFill(t.series.fill, color);
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j],
						hv = ht(v),
						width = hv - baselineWidth,
						w = Math.abs(width);
					if(w >= 1 && height >= 1){
						var shape = s.createRect({
							x: offsets.l + (v < baseline ? hv : baselineWidth),
							y: dim.height - offsets.b - vt(j + 1.5) + gap,
							width: w, height: height
						}).setFill(fill).setStroke(stroke);
						run.dyn.fill   = shape.getFill();
						run.dyn.stroke = shape.getStroke();
						if(events){
							var o = {
								element: "bar",
								index:   j,
								run:     run,
								plot:    this,
								hAxis:   this.hAxis || null,
								vAxis:   this.vAxis || null,
								shape:   shape,
								x:       v,
								y:       j + 1.5
							};
							this._connectEvents(shape, o);
						}
					}
				}
				run.dirty = false;
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.StackedBars"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.StackedBars"] = true;
dojo.provide("dojox.charting.plot2d.StackedBars");







(function(){
	var df = dojox.lang.functional, dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.StackedBars", dojox.charting.plot2d.Bars, {
		calculateAxes: function(dim){
			var stats = dc.collectStackedStats(this.series), t;
			this._maxRunLength = stats.hmax;
			stats.hmin -= 0.5;
			stats.hmax += 0.5;
			t = stats.hmin, stats.hmin = stats.vmin, stats.vmin = t;
			t = stats.hmax, stats.hmax = stats.vmax, stats.vmax = t;
			this._calc(dim, stats);
			return this;
		},
		render: function(dim, offsets){
			if(this._maxRunLength <= 0){
				return this;
			}

			// stack all values
			var acc = df.repeat(this._maxRunLength, "-> 0", 0);
			for(var i = 0; i < this.series.length; ++i){
				var run = this.series[i];
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j];
					if(isNaN(v)){ v = 0; }
					acc[j] += v;
				}
			}
			// draw runs in backwards
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}
			var t = this.chart.theme, color, stroke, fill, f, gap, height,
				ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
				vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler),
				events = this.events();
			f = dc.calculateBarSize(this._vScaler.bounds.scale, this.opt);
			gap = f.gap;
			height = f.size;
			this.resetEvents();
			for(var i = this.series.length - 1; i >= 0; --i){
				var run = this.series[i];
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				var s = run.group;
				if(!run.fill || !run.stroke){
					// need autogenerated color
					color = run.dyn.color = new dojo.Color(t.next("color"));
				}
				stroke = run.stroke ? run.stroke : dc.augmentStroke(t.series.stroke, color);
				fill = run.fill ? run.fill : dc.augmentFill(t.series.fill, color);
				for(var j = 0; j < acc.length; ++j){
					var v = acc[j],
						width  = ht(v);
					if(width >= 1 && height >= 1){
						var shape = s.createRect({
							x: offsets.l,
							y: dim.height - offsets.b - vt(j + 1.5) + gap,
							width: width, height: height
						}).setFill(fill).setStroke(stroke);
						run.dyn.fill   = shape.getFill();
						run.dyn.stroke = shape.getStroke();
						if(events){
							var o = {
								element: "bar",
								index:   j,
								run:     run,
								plot:    this,
								hAxis:   this.hAxis || null,
								vAxis:   this.vAxis || null,
								shape:   shape,
								x:       v,
								y:       j + 1.5
							};
							this._connectEvents(shape, o);
						}
					}
				}
				run.dirty = false;
				// update the accumulator
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j];
					if(isNaN(v)){ v = 0; }
					acc[j] -= v;
				}
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.ClusteredBars"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.ClusteredBars"] = true;
dojo.provide("dojox.charting.plot2d.ClusteredBars");







(function(){
	var df = dojox.lang.functional, dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.ClusteredBars", dojox.charting.plot2d.Bars, {
		render: function(dim, offsets){
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}
			var t = this.chart.theme, color, stroke, fill, f, gap, height, thickness,
				ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
				vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler),
				baseline = Math.max(0, this._hScaler.bounds.lower),
				baselineWidth = ht(baseline),
				events = this.events();
			f = dc.calculateBarSize(this._vScaler.bounds.scale, this.opt, this.series.length);
			gap = f.gap;
			height = thickness = f.size;
			this.resetEvents();
			for(var i = this.series.length - 1; i >= 0; --i){
				var run = this.series[i], shift = thickness * (this.series.length - i - 1);
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				var s = run.group;
				if(!run.fill || !run.stroke){
					// need autogenerated color
					color = run.dyn.color = new dojo.Color(t.next("color"));
				}
				stroke = run.stroke ? run.stroke : dc.augmentStroke(t.series.stroke, color);
				fill = run.fill ? run.fill : dc.augmentFill(t.series.fill, color);
				for(var j = 0; j < run.data.length; ++j){
					var v = run.data[j],
						hv = ht(v),
						width = hv - baselineWidth,
						w = Math.abs(width);
					if(w >= 1 && height >= 1){
						var shape = s.createRect({
							x: offsets.l + (v < baseline ? hv : baselineWidth),
							y: dim.height - offsets.b - vt(j + 1.5) + gap + shift,
							width: w, height: height
						}).setFill(fill).setStroke(stroke);
						run.dyn.fill   = shape.getFill();
						run.dyn.stroke = shape.getStroke();
						if(events){
							var o = {
								element: "bar",
								index:   j,
								run:     run,
								plot:    this,
								hAxis:   this.hAxis || null,
								vAxis:   this.vAxis || null,
								shape:   shape,
								x:       v,
								y:       j + 1.5
							};
							this._connectEvents(shape, o);
						}
					}
				}
				run.dirty = false;
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.Grid"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Grid"] = true;
dojo.provide("dojox.charting.plot2d.Grid");





(function(){
	var du = dojox.lang.utils;

	dojo.declare("dojox.charting.plot2d.Grid", dojox.charting.Element, {
		defaultParams: {
			hAxis: "x",			// use a horizontal axis named "x"
			vAxis: "y",			// use a vertical axis named "y"
			hMajorLines: true,	// draw horizontal major lines
			hMinorLines: false,	// draw horizontal minor lines
			vMajorLines: true,	// draw vertical major lines
			vMinorLines: false,	// draw vertical minor lines
			hStripes: "none",	// TBD
			vStripes: "none"	// TBD
		},
		optionalParams: {},	// no optional parameters

		constructor: function(chart, kwArgs){
			this.opt = dojo.clone(this.defaultParams);
			du.updateWithObject(this.opt, kwArgs);
			this.hAxis = this.opt.hAxis;
			this.vAxis = this.opt.vAxis;
			this.dirty = true;
		},
		clear: function(){
			this._hAxis = null;
			this._vAxis = null;
			this.dirty = true;
			return this;
		},
		setAxis: function(axis){
			if(axis){
				this[axis.vertical ? "_vAxis" : "_hAxis"] = axis;
			}
			return this;
		},
		addSeries: function(run){
			// nothing
			return this;
		},
		calculateAxes: function(dim){
			// nothing
			return this;
		},
		isDirty: function(){
			return this.dirty || this._hAxis && this._hAxis.dirty || this._vAxis && this._vAxis.dirty;
		},
		getRequiredColors: function(){
			return 0;
		},
		render: function(dim, offsets){
			this.dirty = this.isDirty();
			if(!this.dirty){ return this; }
			this.cleanGroup();
			var s = this.group, ta = this.chart.theme.axis;
			// draw horizontal stripes and lines
			try{
				var vScaler = this._vAxis.getScaler(),
					vt = vScaler.scaler.getTransformerFromModel(vScaler),
					ticks = this._vAxis.getTicks();
				if(this.opt.hMinorLines){
					dojo.forEach(ticks.minor, function(tick){
						var y = dim.height - offsets.b - vt(tick.value);
						s.createLine({
							x1: offsets.l,
							y1: y,
							x2: dim.width - offsets.r,
							y2: y
						}).setStroke(ta.minorTick);
					});
				}
				if(this.opt.hMajorLines){
					dojo.forEach(ticks.major, function(tick){
						var y = dim.height - offsets.b - vt(tick.value);
						s.createLine({
							x1: offsets.l,
							y1: y,
							x2: dim.width - offsets.r,
							y2: y
						}).setStroke(ta.majorTick);
					});
				}
			}catch(e){
				// squelch
			}
			// draw vertical stripes and lines
			try{
				var hScaler = this._hAxis.getScaler(),
					ht = hScaler.scaler.getTransformerFromModel(hScaler),
					ticks = this._hAxis.getTicks();
				if(ticks && this.opt.vMinorLines){
					dojo.forEach(ticks.minor, function(tick){
						var x = offsets.l + ht(tick.value);
						s.createLine({
							x1: x,
							y1: offsets.t,
							x2: x,
							y2: dim.height - offsets.b
						}).setStroke(ta.minorTick);
					});
				}
				if(ticks && this.opt.vMajorLines){
					dojo.forEach(ticks.major, function(tick){
						var x = offsets.l + ht(tick.value);
						s.createLine({
							x1: x,
							y1: offsets.t,
							x2: x,
							y2: dim.height - offsets.b
						}).setStroke(ta.majorTick);
					});
				}
			}catch(e){
				// squelch
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.Pie"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Pie"] = true;
dojo.provide("dojox.charting.plot2d.Pie");








(function(){
	var df = dojox.lang.functional, du = dojox.lang.utils,
		dc = dojox.charting.plot2d.common,
		da = dojox.charting.axis2d.common,
		g = dojox.gfx;

	dojo.declare("dojox.charting.plot2d.Pie", dojox.charting.Element, {
		defaultParams: {
			labels:			true,
			ticks:			false,
			fixed:			true,
			precision:		1,
			labelOffset:	20,
			labelStyle:		"default",	// default/rows/auto
			htmlLabels:		true		// use HTML to draw labels
		},
		optionalParams: {
			font:		"",
			fontColor:	"",
			radius:		0
		},

		constructor: function(chart, kwArgs){
			this.opt = dojo.clone(this.defaultParams);
			du.updateWithObject(this.opt, kwArgs);
			du.updateWithPattern(this.opt, kwArgs, this.optionalParams);
			this.run = null;
			this.dyn = [];
		},
		destroy: function(){
			this.resetEvents();
			this.inherited(arguments);
		},
		clear: function(){
			this.dirty = true;
			this.dyn = [];
			this.run = null;
			return this;
		},
		setAxis: function(axis){
			// nothing
			return this;
		},
		addSeries: function(run){
			this.run = run;
			return this;
		},
		calculateAxes: function(dim){
			// nothing
			return this;
		},
		getRequiredColors: function(){
			return this.run ? this.run.data.length : 0;
		},

		// events
		plotEvent: function(o){
			// intentionally empty --- used for events
		},
		connect: function(object, method){
			this.dirty = true;
			return dojo.connect(this, "plotEvent", object, method);
		},
		events: function(){
			var ls = this.plotEvent._listeners;
			if(!ls || !ls.length){ return false; }
			for(var i in ls){
				if(!(i in Array.prototype)){
					return true;
				}
			}
			return false;
		},
		resetEvents: function(){
			this.plotEvent({type: "onplotreset", plot: this});
		},
		_connectEvents: function(shape, o){
			shape.connect("onmouseover", this, function(e){
				o.type  = "onmouseover";
				o.event = e;
				this.plotEvent(o);
			});
			shape.connect("onmouseout", this, function(e){
				o.type  = "onmouseout";
				o.event = e;
				this.plotEvent(o);
			});
			shape.connect("onclick", this, function(e){
				o.type  = "onclick";
				o.event = e;
				this.plotEvent(o);
			});
		},

		render: function(dim, offsets){
			if(!this.dirty){ return this; }
			this.dirty = false;
			this.cleanGroup();
			var s = this.group, color, t = this.chart.theme;
			this.resetEvents();

			if(!this.run || !this.run.data.length){
				return this;
			}

			// calculate the geometry
			var rx = (dim.width  - offsets.l - offsets.r) / 2,
				ry = (dim.height - offsets.t - offsets.b) / 2,
				r  = Math.min(rx, ry),
				taFont = "font" in this.opt ? this.opt.font : t.axis.font,
				size = taFont ? g.normalizedLength(g.splitFontString(taFont).size) : 0,
				taFontColor = "fontColor" in this.opt ? this.opt.fontColor : t.axis.fontColor,
				start = 0, step, filteredRun, slices, labels, shift, labelR,
				run = this.run.data,
				events = this.events();
			if(typeof run[0] == "number"){
				filteredRun = df.map(run, "Math.max(x, 0)");
				if(df.every(filteredRun, "<= 0")){
					return this;
				}
				slices = df.map(filteredRun, "/this", df.foldl(filteredRun, "+", 0));
				if(this.opt.labels){
					labels = dojo.map(slices, function(x){
						return x > 0 ? this._getLabel(x * 100) + "%" : "";
					}, this);
				}
			}else{
				filteredRun = df.map(run, "Math.max(x.y, 0)");
				if(df.every(filteredRun, "<= 0")){
					return this;
				}
				slices = df.map(filteredRun, "/this", df.foldl(filteredRun, "+", 0));
				if(this.opt.labels){
					labels = dojo.map(slices, function(x, i){
						if(x <= 0){ return ""; }
						var v = run[i];
						return "text" in v ? v.text : this._getLabel(x * 100) + "%";
					}, this);
				}
			}
			if(this.opt.labels){
				shift = df.foldl1(df.map(labels, function(label){
					return dojox.gfx._base._getTextBox(label, {font: taFont}).w;
				}, this), "Math.max(a, b)") / 2;
				if(this.opt.labelOffset < 0){
					r = Math.min(rx - 2 * shift, ry - size) + this.opt.labelOffset;
				}
				labelR = r - this.opt.labelOffset;
			}
			if("radius" in this.opt){
				r = this.opt.radius;
				labelR = r - this.opt.labelOffset;
			}
			var	circle = {
					cx: offsets.l + rx,
					cy: offsets.t + ry,
					r:  r
				};

			this.dyn = [];
			// draw slices
			dojo.some(slices, function(slice, i){
				if(slice <= 0){
					// degenerated slice
					return false;	// continue
				}
				var v = run[i];
				if(slice >= 1){
					// whole pie
					var color, fill, stroke;
					if(typeof v == "object"){
						color  = "color"  in v ? v.color  : new dojo.Color(t.next("color"));
						fill   = "fill"   in v ? v.fill   : dc.augmentFill(t.series.fill, color);
						stroke = "stroke" in v ? v.stroke : dc.augmentStroke(t.series.stroke, color);
					}else{
						color  = new dojo.Color(t.next("color"));
						fill   = dc.augmentFill(t.series.fill, color);
						stroke = dc.augmentStroke(t.series.stroke, color);
					}
					var shape = s.createCircle(circle).setFill(fill).setStroke(stroke);
					this.dyn.push({color: color, fill: fill, stroke: stroke});

					if(events){
						var o = {
							element: "slice",
							index:   i,
							run:     this.run,
							plot:    this,
							shape:   shape,
							x:       i,
							y:       typeof v == "number" ? v : v.y,
							cx:      circle.cx,
							cy:      circle.cy,
							cr:      r
						};
						this._connectEvents(shape, o);
					}

					return true;	// stop iteration
				}
				// calculate the geometry of the slice
				var end = start + slice * 2 * Math.PI;
				if(i + 1 == slices.length){
					end = 2 * Math.PI;
				}
				var	step = end - start,
					x1 = circle.cx + r * Math.cos(start),
					y1 = circle.cy + r * Math.sin(start),
					x2 = circle.cx + r * Math.cos(end),
					y2 = circle.cy + r * Math.sin(end);
				// draw the slice
				var color, fill, stroke;
				if(typeof v == "object"){
					color  = "color"  in v ? v.color  : new dojo.Color(t.next("color"));
					fill   = "fill"   in v ? v.fill   : dc.augmentFill(t.series.fill, color);
					stroke = "stroke" in v ? v.stroke : dc.augmentStroke(t.series.stroke, color);
				}else{
					color  = new dojo.Color(t.next("color"));
					fill   = dc.augmentFill(t.series.fill, color);
					stroke = dc.augmentStroke(t.series.stroke, color);
				}
				var shape = s.createPath({}).
						moveTo(circle.cx, circle.cy).
						lineTo(x1, y1).
						arcTo(r, r, 0, step > Math.PI, true, x2, y2).
						lineTo(circle.cx, circle.cy).
						closePath().
						setFill(fill).
						setStroke(stroke);
				this.dyn.push({color: color, fill: fill, stroke: stroke});

				if(events){
					var o = {
						element: "slice",
						index:   i,
						run:     this.run,
						plot:    this,
						shape:   shape,
						x:       i,
						y:       typeof v == "number" ? v : v.y,
						cx:      circle.cx,
						cy:      circle.cy,
						cr:      r
					};
					this._connectEvents(shape, o);
				}

				start = end;

				return false;	// continue
			}, this);
			// draw labels
			if(this.opt.labels){
				start = 0;
				dojo.some(slices, function(slice, i){
					if(slice <= 0){
						// degenerated slice
						return false;	// continue
					}
					if(slice >= 1){
						// whole pie
						var v = run[i], elem = da.createText[this.opt.htmlLabels && dojox.gfx.renderer != "vml" ? "html" : "gfx"]
									(this.chart, s, circle.cx, circle.cy + size / 2, "middle",
										labels[i], taFont, (typeof v == "object" && "fontColor" in v) ? v.fontColor : taFontColor);
						if(this.opt.htmlLabels){ this.htmlElements.push(elem); }
						return true;	// stop iteration
					}
					// calculate the geometry of the slice
					var end = start + slice * 2 * Math.PI, v = run[i];
					if(i + 1 == slices.length){
						end = 2 * Math.PI;
					}
					var	labelAngle = (start + end) / 2,
						x = circle.cx + labelR * Math.cos(labelAngle),
						y = circle.cy + labelR * Math.sin(labelAngle) + size / 2;
					// draw the label
					var elem = da.createText[this.opt.htmlLabels && dojox.gfx.renderer != "vml" ? "html" : "gfx"]
									(this.chart, s, x, y, "middle",
										labels[i], taFont,
										(typeof v == "object" && "fontColor" in v)
											? v.fontColor : taFontColor);
					if(this.opt.htmlLabels){ this.htmlElements.push(elem); }
					start = end;
					return false;	// continue
				}, this);
			}
			return this;
		},

		// utilities
		_getLabel: function(number){
			return this.opt.fixed ? number.toFixed(this.opt.precision) : number.toString();
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.plot2d.Bubble"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.plot2d.Bubble"] = true;
dojo.provide("dojox.charting.plot2d.Bubble");




(function(){
	var df = dojox.lang.functional, du = dojox.lang.utils,
		dc = dojox.charting.plot2d.common,
		purgeGroup = df.lambda("item.purgeGroup()");

	dojo.declare("dojox.charting.plot2d.Bubble", dojox.charting.plot2d.Base, {
		defaultParams: {
			hAxis: "x",		// use a horizontal axis named "x"
			vAxis: "y"		// use a vertical axis named "y"
		},
		optionalParams: {},	// no optional parameters

		constructor: function(chart, kwArgs){
			this.opt = dojo.clone(this.defaultParams);
			du.updateWithObject(this.opt, kwArgs);
			this.series = [];
			this.hAxis = this.opt.hAxis;
			this.vAxis = this.opt.vAxis;
		},
		
		calculateAxes: function(dim){
			this._calc(dim, dc.collectSimpleStats(this.series));
			return this;
		},

		//	override the render so that we are plotting only circles.
		render: function(dim, offsets){
			this.dirty = this.isDirty();
			if(this.dirty){
				dojo.forEach(this.series, purgeGroup);
				this.cleanGroup();
				var s = this.group;
				df.forEachRev(this.series, function(item){ item.cleanGroup(s); });
			}
		
			var t = this.chart.theme, stroke, outline, color, shadowStroke, shadowColor,
				ht = this._hScaler.scaler.getTransformerFromModel(this._hScaler),
				vt = this._vScaler.scaler.getTransformerFromModel(this._vScaler),
				events = this.events();

			this.resetEvents();

			for(var i = this.series.length - 1; i >= 0; --i){
				var run = this.series[i];
				if(!this.dirty && !run.dirty){ continue; }
				run.cleanGroup();
				if(!run.data.length){
					run.dirty = false;
					continue;
				}

				if(typeof run.data[0] == "number"){
					console.warn("dojox.charting.plot2d.Bubble: the data in the following series cannot be rendered as a bubble chart; ", run);
					continue;
				}
				
				var s = run.group,
					points = dojo.map(run.data, function(v, i){
						return {
							x: ht(v.x) + offsets.l,
							y: dim.height - offsets.b - vt(v.y),
							radius: this._vScaler.bounds.scale * (v.size / 2)
						};
					}, this);

				if(run.fill){
					color = run.fill;
				}else if(run.stroke){
					color = run.stroke;
				}else{
					color = run.dyn.color = new dojo.Color(t.next("color"));
				}
				run.dyn.fill = color;

				stroke = run.dyn.stroke = run.stroke ? dc.makeStroke(run.stroke) : dc.augmentStroke(t.series.stroke, color);

				var frontCircles = null, outlineCircles = null, shadowCircles = null;

				// make shadows if needed
				if(this.opt.shadows && stroke){
					var sh = this.opt.shadows, shadowColor = new dojo.Color([0, 0, 0, 0.2]),
						shadowStroke = dojo.clone(outline ? outline : stroke);
					shadowStroke.color = shadowColor;
					shadowStroke.width += sh.dw ? sh.dw : 0;
					run.dyn.shadow = shadowStroke;
					var shadowMarkers = dojo.map(points, function(item){
						var sh = this.opt.shadows;
						return s.createCircle({
							cx: item.x + sh.dx, cy: item.y + sh.dy, r: item.radius
						}).setStroke(shadowStroke).setFill(shadowColor);
					}, this);
				}

				// make outlines if needed
				if(run.outline || t.series.outline){
					outline = dc.makeStroke(run.outline ? run.outline : t.series.outline);
					outline.width = 2 * outline.width + stroke.width;
					run.dyn.outline = outline;
					outlineCircles = dojo.map(points, function(item){
						s.createCircle({ cx: item.x, cy: item.y, r: item.radius }).setStroke(outline);
					}, this);
				}

				//	run through the data and add the circles.
				frontCircles = dojo.map(points, function(item){
					return s.createCircle({ cx: item.x, cy: item.y, r: item.radius }).setStroke(stroke).setFill(color);
				}, this);
				
				if(events){
					dojo.forEach(frontCircles, function(s, i){
						var o = {
							element: "circle",
							index:   i,
							run:     run,
							plot:    this,
							hAxis:   this.hAxis || null,
							vAxis:   this.vAxis || null,
							shape:   s,
							outline: outlineCircles && outlineCircles[i] || null,
							shadow:  shadowCircles && shadowCircles[i] || null,
							x:       run.data[i].x,
							y:       run.data[i].y,
							r:       run.data[i].size / 2,
							cx:      points[i].x,
							cy:      points[i].y,
							cr:      points[i].radius
						};
						this._connectEvents(s, o);
					}, this);
				}
				
				run.dirty = false;
			}
			this.dirty = false;
			return this;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.Chart2D"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.Chart2D"] = true;
dojo.provide("dojox.charting.Chart2D");









// require all axes to support references by name


// require all plots to support references by name



















(function(){
	var df = dojox.lang.functional, dc = dojox.charting,
		clear = df.lambda("item.clear()"),
		purge = df.lambda("item.purgeGroup()"),
		destroy = df.lambda("item.destroy()"),
		makeClean = df.lambda("item.dirty = false"),
		makeDirty = df.lambda("item.dirty = true");

	dojo.declare("dojox.charting.Chart2D", null, {
		constructor: function(node, kwArgs){
			// initialize parameters
			if(!kwArgs){ kwArgs = {}; }
			this.margins = kwArgs.margins ? kwArgs.margins : {l: 10, t: 10, r: 10, b: 10};
			this.stroke  = kwArgs.stroke;
			this.fill    = kwArgs.fill;

			// default initialization
			this.theme = null;
			this.axes = {};		// map of axes
			this.stack = [];	// stack of plotters
			this.plots = {};	// map of plotter indices
			this.series = [];	// stack of data runs
			this.runs = {};		// map of data run indices
			this.dirty = true;
			this.coords = null;

			// create a surface
			this.node = dojo.byId(node);
			var box = dojo.marginBox(node);
			this.surface = dojox.gfx.createSurface(this.node, box.w, box.h);
		},
		destroy: function(){
			dojo.forEach(this.series, destroy);
			dojo.forEach(this.stack,  destroy);
			df.forIn(this.axes, destroy);
			this.surface.destroy();
		},
		getCoords: function(){
			if(!this.coords){
				this.coords = dojo.coords(this.node, true);
			}
			return this.coords;
		},
		setTheme: function(theme){
			this.theme = theme._clone();
			this.dirty = true;
			return this;
		},
		addAxis: function(name, kwArgs){
			var axis;
			if(!kwArgs || !("type" in kwArgs)){
				axis = new dc.axis2d.Default(this, kwArgs);
			}else{
				axis = typeof kwArgs.type == "string" ?
					new dc.axis2d[kwArgs.type](this, kwArgs) :
					new kwArgs.type(this, kwArgs);
			}
			axis.name = name;
			axis.dirty = true;
			if(name in this.axes){
				this.axes[name].destroy();
			}
			this.axes[name] = axis;
			this.dirty = true;
			return this;
		},
		getAxis: function(name){
			return this.axes[name];
		},
		removeAxis: function(name){
			if(name in this.axes){
				// destroy the axis
				this.axes[name].destroy();
				delete this.axes[name];
				// mark the chart as dirty
				this.dirty = true;
			}
			return this;	// self
		},
		addPlot: function(name, kwArgs){
			var plot;
			if(!kwArgs || !("type" in kwArgs)){
				plot = new dc.plot2d.Default(this, kwArgs);
			}else{
				plot = typeof kwArgs.type == "string" ?
					new dc.plot2d[kwArgs.type](this, kwArgs) :
					new kwArgs.type(this, kwArgs);
			}
			plot.name = name;
			plot.dirty = true;
			if(name in this.plots){
				this.stack[this.plots[name]].destroy();
				this.stack[this.plots[name]] = plot;
			}else{
				this.plots[name] = this.stack.length;
				this.stack.push(plot);
			}
			this.dirty = true;
			return this;
		},
		removePlot: function(name){
			if(name in this.plots){
				// get the index and remove the name
				var index = this.plots[name];
				delete this.plots[name];
				// destroy the plot
				this.stack[index].destroy();
				// remove the plot from the stack
				this.stack.splice(index, 1);
				// update indices to reflect the shift
				df.forIn(this.plots, function(idx, name, plots){
					if(idx > index){
						plots[name] = idx - 1;
					}
				});
				// mark the chart as dirty
				this.dirty = true;
			}
			return this;	// self
		},
		addSeries: function(name, data, kwArgs){
			var run = new dc.Series(this, data, kwArgs);
			if(name in this.runs){
				this.series[this.runs[name]].destroy();
				this.series[this.runs[name]] = run;
			}else{
				this.runs[name] = this.series.length;
				this.series.push(run);
			}
			run.name = name;
			this.dirty = true;
			// fix min/max
			if(!("ymin" in run) && "min" in run){ run.ymin = run.min; }
			if(!("ymax" in run) && "max" in run){ run.ymax = run.max; }
			return this;
		},
		removeSeries: function(name){
			if(name in this.runs){
				// get the index and remove the name
				var index = this.runs[name],
					plotName = this.series[index].plot;
				delete this.runs[name];
				// destroy the run
				this.series[index].destroy();
				// remove the run from the stack of series
				this.series.splice(index, 1);
				// update indices to reflect the shift
				df.forIn(this.runs, function(idx, name, runs){
					if(idx > index){
						runs[name] = idx - 1;
					}
				});
				this.dirty = true;
			}
			return this;	// self
		},
		updateSeries: function(name, data){
			if(name in this.runs){
				var run = this.series[this.runs[name]];
				run.data = data;
				run.dirty = true;
				this._invalidateDependentPlots(run.plot, false);
				this._invalidateDependentPlots(run.plot, true);
			}
			return this;
		},
		resize: function(width, height){
			var box;
			switch(arguments.length){
				case 0:
					box = dojo.marginBox(this.node);
					break;
				case 1:
					box = width;
					break;
				default:
					box = {w: width, h: height};
					break;
			}
			dojo.marginBox(this.node, box);
			this.surface.setDimensions(box.w, box.h);
			this.dirty = true;
			this.coords = null;
			return this.render();
		},
		getGeometry: function(){
			var ret = {};
			df.forIn(this.axes, function(axis){
				if(axis.initialized()){
					ret[axis.name] = {
						name:		axis.name,
						vertical:	axis.vertical,
						scaler:		axis.scaler,
						ticks:		axis.ticks
					};
				}
			});
			return ret;
		},
		setAxisWindow: function(name, scale, offset){
			var axis = this.axes[name];
			if(axis){
				axis.setWindow(scale, offset);
			}
			return this;
		},
		setWindow: function(sx, sy, dx, dy){
			if(!("plotArea" in this)){
				this.calculateGeometry();
			}
			df.forIn(this.axes, function(axis){
				var scale, offset, bounds = axis.getScaler().bounds,
					s = bounds.span / (bounds.upper - bounds.lower);
				if(axis.vertical){
					scale  = sy;
					offset = dy / s / scale;
				}else{
					scale  = sx;
					offset = dx / s / scale;
				}
				axis.setWindow(scale, offset);
			});
			return this;
		},
		calculateGeometry: function(){
			if(this.dirty){
				return this.fullGeometry();
			}

			// calculate geometry
			dojo.forEach(this.stack, function(plot){
				if(plot.dirty || (plot.hAxis && this.axes[plot.hAxis].dirty) ||
						(plot.vAxis && this.axes[plot.vAxis].dirty)){
					plot.calculateAxes(this.plotArea);
				}
			}, this);

			return this;
		},
		fullGeometry: function(){
			this._makeDirty();

			// clear old values
			dojo.forEach(this.stack, clear);

			// rebuild new connections, and add defaults

			// set up a theme
			if(!this.theme){
				this.setTheme(new dojox.charting.Theme(dojox.charting._def));
			}

			// assign series
			dojo.forEach(this.series, function(run){
				if(!(run.plot in this.plots)){
					var plot = new dc.plot2d.Default(this, {});
					plot.name = run.plot;
					this.plots[run.plot] = this.stack.length;
					this.stack.push(plot);
				}
				this.stack[this.plots[run.plot]].addSeries(run);
			}, this);
			// assign axes
			dojo.forEach(this.stack, function(plot){
				if(plot.hAxis){
					plot.setAxis(this.axes[plot.hAxis]);
				}
				if(plot.vAxis){
					plot.setAxis(this.axes[plot.vAxis]);
				}
			}, this);

			// calculate geometry

			// 1st pass
			var dim = this.dim = this.surface.getDimensions();
			dim.width  = dojox.gfx.normalizedLength(dim.width);
			dim.height = dojox.gfx.normalizedLength(dim.height);
			df.forIn(this.axes, clear);
			dojo.forEach(this.stack, function(plot){ plot.calculateAxes(dim); });

			// assumption: we don't have stacked axes yet
			var offsets = this.offsets = {l: 0, r: 0, t: 0, b: 0};
			df.forIn(this.axes, function(axis){
				df.forIn(axis.getOffsets(), function(o, i){ offsets[i] += o; });
			});
			// add margins
			df.forIn(this.margins, function(o, i){ offsets[i] += o; });

			// 2nd pass with realistic dimensions
			this.plotArea = {width: dim.width - offsets.l - offsets.r, height: dim.height - offsets.t - offsets.b};
			df.forIn(this.axes, clear);
			dojo.forEach(this.stack, function(plot){ plot.calculateAxes(this.plotArea); }, this);

			return this;
		},
		render: function(){
			if(this.theme){
				this.theme.clear();
			}

			if(this.dirty){
				return this.fullRender();
			}

			this.calculateGeometry();

			// go over the stack backwards
			df.forEachRev(this.stack, function(plot){ plot.render(this.dim, this.offsets); }, this);

			// go over axes
			df.forIn(this.axes, function(axis){ axis.render(this.dim, this.offsets); }, this);

			this._makeClean();

			// BEGIN FOR HTML CANVAS
			if(this.surface.render){ this.surface.render(); };
			// END FOR HTML CANVAS

			return this;
		},
		fullRender: function(){
			// calculate geometry
			this.fullGeometry();
			var offsets = this.offsets, dim = this.dim;

			// get required colors
			var requiredColors = df.foldl(this.stack, "z + plot.getRequiredColors()", 0);
			this.theme.defineColors({num: requiredColors, cache: false});

			// clear old shapes
			dojo.forEach(this.series, purge);
			df.forIn(this.axes, purge);
			dojo.forEach(this.stack,  purge);
			this.surface.clear();

			// generate shapes

			// draw a plot background
			var t = this.theme,
				fill   = t.plotarea && t.plotarea.fill,
				stroke = t.plotarea && t.plotarea.stroke;
			if(fill){
				this.surface.createRect({
					x: offsets.l, y: offsets.t,
					width:  dim.width  - offsets.l - offsets.r,
					height: dim.height - offsets.t - offsets.b
				}).setFill(fill);
			}
			if(stroke){
				this.surface.createRect({
					x: offsets.l, y: offsets.t,
					width:  dim.width  - offsets.l - offsets.r - 1,
					height: dim.height - offsets.t - offsets.b - 1
				}).setStroke(stroke);
			}

			// go over the stack backwards
			df.foldr(this.stack, function(z, plot){ return plot.render(dim, offsets), 0; }, 0);

			// pseudo-clipping: matting
			fill   = this.fill   ? this.fill   : (t.chart && t.chart.fill);
			stroke = this.stroke ? this.stroke : (t.chart && t.chart.stroke);

			//	TRT: support for "inherit" as a named value in a theme.
			if(fill == "inherit"){
				//	find the background color of the nearest ancestor node, and use that explicitly.
				var node = this.node, fill = new dojo.Color(dojo.style(node, "backgroundColor"));
				while(fill.a==0 && node!=document.documentElement){
					fill = new dojo.Color(dojo.style(node, "backgroundColor"));
					node = node.parentNode;
				}
			}

			if(fill){
				if(offsets.l){	// left
					this.surface.createRect({
						width:  offsets.l,
						height: dim.height + 1
					}).setFill(fill);
				}
				if(offsets.r){	// right
					this.surface.createRect({
						x: dim.width - offsets.r,
						width:  offsets.r + 1,
						height: dim.height + 1
					}).setFill(fill);
				}
				if(offsets.t){	// top
					this.surface.createRect({
						width:  dim.width + 1,
						height: offsets.t
					}).setFill(fill);
				}
				if(offsets.b){	// bottom
					this.surface.createRect({
						y: dim.height - offsets.b,
						width:  dim.width + 1,
						height: offsets.b + 2
					}).setFill(fill);
				}
			}
			if(stroke){
				this.surface.createRect({
					width:  dim.width - 1,
					height: dim.height - 1
				}).setStroke(stroke);
			}

			// go over axes
			df.forIn(this.axes, function(axis){ axis.render(dim, offsets); });

			this._makeClean();

			// BEGIN FOR HTML CANVAS
			if(this.surface.render){ this.surface.render(); };
			// END FOR HTML CANVAS

			return this;
		},
		connectToPlot: function(name, object, method){
			return name in this.plots ? this.stack[this.plots[name]].connect(object, method) : null;
		},
		_makeClean: function(){
			// reset dirty flags
			dojo.forEach(this.axes,   makeClean);
			dojo.forEach(this.stack,  makeClean);
			dojo.forEach(this.series, makeClean);
			this.dirty = false;
		},
		_makeDirty: function(){
			// reset dirty flags
			dojo.forEach(this.axes,   makeDirty);
			dojo.forEach(this.stack,  makeDirty);
			dojo.forEach(this.series, makeDirty);
			this.dirty = true;
		},
		_invalidateDependentPlots: function(plotName, /* Boolean */ verticalAxis){
			if(plotName in this.plots){
				var plot = this.stack[this.plots[plotName]], axis,
					axisName = verticalAxis ? "vAxis" : "hAxis";
				if(plot[axisName]){
					axis = this.axes[plot[axisName]];
					if(axis.dependOnData()){
						axis.dirty = true;
						// find all plots and mark them dirty
						dojo.forEach(this.stack, function(p){
							if(p[axisName] && p[axisName] == plot[axisName]){
								p.dirty = true;
							}
						});
					}
				}else{
					plot.dirty = true;
				}
			}
		}
	});
})();

}

if(!dojo._hasResource["dojo.fx.easing"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojo.fx.easing"] = true;
dojo.provide("dojo.fx.easing");

dojo.fx.easing = {
	// summary: Collection of easing functions to use beyond the default dojo._defaultEasing
	// 
	// description:
	//	Easing functions are used to manipulate the iteration through
	//	an _Animation's _Line. _Line being the properties of an Animation,
	//	and the easing function progresses through that Line determing
	//	how quickly (or slowly) it should go. Or more accurately: modify
	//	the value of the _Line based on the percentage of animation completed.
	//	
	//	All functions follow a simple naming convention of "ease type" + "when".
	//	If the name of the function ends in Out, the easing described appears
	//	towards the end of the animation. "In" means during the beginning,
	//	and InOut means both ranges of the Animation will applied, both beginning
	//	and end. 
	//
	//	example:
	//	|	
	//	|	var anim = dojo.fadeOut({
	//	|		node: 'node',	
	//	|		duration: 2000,
	//	|		easing: dojo.fx.easing.quadIn
	//	|	}).play();
	//
	
	linear: function(/* Decimal? */n){
		// summary: A linear easing function
		return n;
	},

	quadIn: function(/* Decimal? */n){
		return Math.pow(n, 2);
	},

	quadOut: function(/* Decimal? */n){
		return n * (n - 2) * -1;
	},

	quadInOut: function(/* Decimal? */n){
		n = n * 2;
		if(n < 1){ return Math.pow(n, 2) / 2; }
		return -1 * ((--n) * (n - 2) - 1) / 2;
	},

	cubicIn: function(/* Decimal? */n){
		return Math.pow(n, 3);
	},

	cubicOut: function(/* Decimal? */n){
		return Math.pow(n - 1, 3) + 1;
	},

	cubicInOut: function(/* Decimal? */n){
		n = n * 2;
		if(n < 1){ return Math.pow(n, 3) / 2; }
		n -= 2;
		return (Math.pow(n, 3) + 2) / 2;
	},

	quartIn: function(/* Decimal? */n){
		return Math.pow(n, 4);
	},

	quartOut: function(/* Decimal? */n){
		return -1 * (Math.pow(n - 1, 4) - 1);
	},

	quartInOut: function(/* Decimal? */n){
		n = n * 2;
		if(n < 1){ return Math.pow(n, 4) / 2; }
		n -= 2;
		return -1 / 2 * (Math.pow(n, 4) - 2);
	},

	quintIn: function(/* Decimal? */n){
		return Math.pow(n, 5);
	},

	quintOut: function(/* Decimal? */n){
		return Math.pow(n - 1, 5) + 1;
	},

	quintInOut: function(/* Decimal? */n){
		n = n * 2;
		if(n < 1){ return Math.pow(n, 5) / 2; };
		n -= 2;
		return (Math.pow(n, 5) + 2) / 2;
	},

	sineIn: function(/* Decimal? */n){
		return -1 * Math.cos(n * (Math.PI / 2)) + 1;
	},

	sineOut: function(/* Decimal? */n){
		return Math.sin(n * (Math.PI / 2));
	},

	sineInOut: function(/* Decimal? */n){
		return -1 * (Math.cos(Math.PI * n) - 1) / 2;
	},

	expoIn: function(/* Decimal? */n){
		return (n == 0) ? 0 : Math.pow(2, 10 * (n - 1));
	},

	expoOut: function(/* Decimal? */n){
		return (n == 1) ? 1 : (-1 * Math.pow(2, -10 * n) + 1);
	},

	expoInOut: function(/* Decimal? */n){
		if(n == 0){ return 0; }
		if(n == 1){ return 1; }
		n = n * 2;
		if(n < 1){ return Math.pow(2, 10 * (n - 1)) / 2; }
		--n;
		return (-1 * Math.pow(2, -10 * n) + 2) / 2;
	},

	circIn: function(/* Decimal? */n){
		return -1 * (Math.sqrt(1 - Math.pow(n, 2)) - 1);
	},

	circOut: function(/* Decimal? */n){
		n = n - 1;
		return Math.sqrt(1 - Math.pow(n, 2));
	},

	circInOut: function(/* Decimal? */n){
		n = n * 2;
		if(n < 1){ return -1 / 2 * (Math.sqrt(1 - Math.pow(n, 2)) - 1); }
		n -= 2;
		return 1 / 2 * (Math.sqrt(1 - Math.pow(n, 2)) + 1);
	},

	backIn: function(/* Decimal? */n){
		// summary: An easing function that starts away from the target, and 
		//		quickly accelerates towards the end value
		var s = 1.70158;
		return Math.pow(n, 2) * ((s + 1) * n - s);
	},

	backOut: function(/* Decimal? */n){
		// summary: An easing function that pops past the range briefly, and 
		// 	slowly comes back. 
		n = n - 1;
		var s = 1.70158;
		return Math.pow(n, 2) * ((s + 1) * n + s) + 1;
	},

	backInOut: function(/* Decimal? */n){
		// summary: An easing function combining the effects of backIn and backOut
		var s = 1.70158 * 1.525;
		n = n * 2;
		if(n < 1){ return (Math.pow(n, 2) * ((s + 1) * n - s)) / 2; }
		n-=2;
		return (Math.pow(n, 2) * ((s + 1) * n + s) + 2) / 2;
	},

	elasticIn: function(/* Decimal? */n){
		// summary: An easing function the elastically snaps from the start value
		if(n == 0 || n == 1){ return n; }
		var p = .3;
		var s = p / 4;
		n = n - 1;
		return -1 * Math.pow(2, 10 * n) * Math.sin((n - s) * (2 * Math.PI) / p);
	},

	elasticOut: function(/* Decimal? */n){
		// summary: An easing function that elasticly snaps around the target value, near the end of the Animation
		if(n==0 || n == 1){ return n; }
		var p = .3;
		var s = p / 4;
		return Math.pow(2, -10 * n) * Math.sin((n - s) * (2 * Math.PI) / p) + 1;
	},

	elasticInOut: function(/* Decimal? */n){
		// summary: An easing function that elasticly snaps around the value, near the beginning and end of the Animation		
		if(n == 0) return 0;
		n = n * 2;
		if(n == 2) return 1;
		var p = .3 * 1.5;
		var s = p / 4;
		if(n < 1){
			n -= 1;
			return -.5 * (Math.pow(2, 10 * n) * Math.sin((n - s) * (2 * Math.PI) / p));
		}
		n -= 1;
		return .5 * (Math.pow(2, -10 * n) * Math.sin((n - s) * (2 * Math.PI) / p)) + 1;
	},

	bounceIn: function(/* Decimal? */n){
		// summary: An easing function that "bounces" near the beginning of an Animation
		return (1 - dojo.fx.easing.bounceOut(1 - n)); // Decimal
	},

	bounceOut: function(/* Decimal? */n){
		// summary: An easing function that "bounces" near the end of an Animation
		var s = 7.5625;
		var p = 2.75;
		var l; 
		if(n < (1 / p)){
			l = s * Math.pow(n, 2);
		}else if(n < (2 / p)){
			n -= (1.5 / p);
			l = s * Math.pow(n, 2) + .75;
		}else if(n < (2.5 / p)){
			n -= (2.25 / p);
			l = s * Math.pow(n, 2) + .9375;
		}else{
			n -= (2.625 / p);
			l = s * Math.pow(n, 2) + .984375;
		}
		return l;
	},

	bounceInOut: function(/* Decimal? */n){
		// summary: An easing function that "bounces" at the beginning and end of the Animation
		if(n < 0.5){ return dojo.fx.easing.bounceIn(n * 2) / 2; }
		return (dojo.fx.easing.bounceOut(n * 2 - 1) / 2) + 0.5; // Decimal
	}
};

}

if(!dojo._hasResource["dojox.gfx.fx"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.gfx.fx"] = true;
dojo.provide("dojox.gfx.fx");



(function(){
	var d = dojo, g = dojox.gfx, m = g.matrix;

	// Generic interpolators. Should they be moved to dojox.fx?

	var InterpolNumber = function(start, end){
		this.start = start, this.end = end;
	};
	d.extend(InterpolNumber, {
		getValue: function(r){
			return (this.end - this.start) * r + this.start;
		}
	});

	var InterpolUnit = function(start, end, unit){
		this.start = start, this.end = end;
		this.unit = unit;
	};
	d.extend(InterpolUnit, {
		getValue: function(r){
			return (this.end - this.start) * r + this.start + this.unit;
		}
	});

	var InterpolColor = function(start, end){
		this.start = start, this.end = end;
		this.temp = new dojo.Color();
	};
	d.extend(InterpolColor, {
		getValue: function(r){
			return d.blendColors(this.start, this.end, r, this.temp);
		}
	});

	var InterpolValues = function(values){
		this.values = values;
		this.length = values.length;
	};
	d.extend(InterpolValues, {
		getValue: function(r){
			return this.values[Math.min(Math.floor(r * this.length), this.length - 1)];
		}
	});

	var InterpolObject = function(values, def){
		this.values = values;
		this.def = def ? def : {};
	};
	d.extend(InterpolObject, {
		getValue: function(r){
			var ret = dojo.clone(this.def);
			for(var i in this.values){
				ret[i] = this.values[i].getValue(r);
			}
			return ret;
		}
	});

	var InterpolTransform = function(stack, original){
		this.stack = stack;
		this.original = original;
	};
	d.extend(InterpolTransform, {
		getValue: function(r){
			var ret = [];
			dojo.forEach(this.stack, function(t){
				if(t instanceof m.Matrix2D){
					ret.push(t);
					return;
				}
				if(t.name == "original" && this.original){
					ret.push(this.original);
					return;
				}
				if(!(t.name in m)){ return; }
				var f = m[t.name];
				if(typeof f != "function"){
					// constant
					ret.push(f);
					return;
				}
				var val = dojo.map(t.start, function(v, i){
								return (t.end[i] - v) * r + v;
							}),
					matrix = f.apply(m, val);
				if(matrix instanceof m.Matrix2D){
					ret.push(matrix);
				}
			}, this);
			return ret;
		}
	});

	var transparent = new d.Color(0, 0, 0, 0);

	var getColorInterpol = function(prop, obj, name, def){
		if(prop.values){
			return new InterpolValues(prop.values);
		}
		var value, start, end;
		if(prop.start){
			start = g.normalizeColor(prop.start);
		}else{
			start = value = obj ? (name ? obj[name] : obj) : def;
		}
		if(prop.end){
			end = g.normalizeColor(prop.end);
		}else{
			if(!value){
				value = obj ? (name ? obj[name] : obj) : def;
			}
			end = value;
		}
		return new InterpolColor(start, end);
	};

	var getNumberInterpol = function(prop, obj, name, def){
		if(prop.values){
			return new InterpolValues(prop.values);
		}
		var value, start, end;
		if(prop.start){
			start = prop.start;
		}else{
			start = value = obj ? obj[name] : def;
		}
		if(prop.end){
			end = prop.end;
		}else{
			if(typeof value != "number"){
				value = obj ? obj[name] : def;
			}
			end = value;
		}
		return new InterpolNumber(start, end);
	};

	g.fx.animateStroke = function(/*Object*/ args){
		// summary:
		//	Returns an animation which will change stroke properties over time
		// example:
		//	|	dojox.gfx.fx.animateStroke{{
		//	|		shape: shape,
		//	|		duration: 500,
		//	|		color: {start: "red", end: "green"},
		//	|		width: {end: 15},
		//	|		join:  {values: ["miter", "bevel", "round"]}
		//	|	}).play();
		if(!args.easing){ args.easing = d._defaultEasing; }
		var anim = new d._Animation(args), shape = args.shape, stroke;
		d.connect(anim, "beforeBegin", anim, function(){
			stroke = shape.getStroke();
			var prop = args.color, values = {}, value, start, end;
			if(prop){
				values.color = getColorInterpol(prop, stroke, "color", transparent);
			}
			prop = args.style;
			if(prop && prop.values){
				values.style = new InterpolValues(prop.values);
			}
			prop = args.width;
			if(prop){
				values.width = getNumberInterpol(prop, stroke, "width", 1);
			}
			prop = args.cap;
			if(prop && prop.values){
				values.cap = new InterpolValues(prop.values);
			}
			prop = args.join;
			if(prop){
				if(prop.values){
					values.join = new InterpolValues(prop.values);
				}else{
					start = prop.start ? prop.start : (stroke && stroke.join || 0);
					end = prop.end ? prop.end : (stroke && stroke.join || 0);
					if(typeof start == "number" && typeof end == "number"){
						values.join = new InterpolNumber(start, end);
					}
				}
			}
			this.curve = new InterpolObject(values, stroke);
		});
		d.connect(anim, "onAnimate", shape, "setStroke");
		return anim; // dojo._Animation
	};

	g.fx.animateFill = function(/*Object*/ args){
		// summary:
		//	Returns an animation which will change fill color over time.
		//	Only solid fill color is supported at the moment
		// example:
		//	|	dojox.gfx.fx.animateFill{{
		//	|		shape: shape,
		//	|		duration: 500,
		//	|		color: {start: "red", end: "green"}
		//	|	}).play();
		if(!args.easing){ args.easing = d._defaultEasing; }
		var anim = new d._Animation(args), shape = args.shape, fill;
		d.connect(anim, "beforeBegin", anim, function(){
			fill = shape.getFill();
			var prop = args.color, values = {};
			if(prop){
				this.curve = getColorInterpol(prop, fill, "", transparent);
			}
		});
		d.connect(anim, "onAnimate", shape, "setFill");
		return anim; // dojo._Animation
	};

	g.fx.animateFont = function(/*Object*/ args){
		// summary:
		//	Returns an animation which will change font properties over time
		// example:
		//	|	dojox.gfx.fx.animateFont{{
		//	|		shape: shape,
		//	|		duration: 500,
		//	|		variant: {values: ["normal", "small-caps"]},
		//	|		size:  {end: 10, unit: "pt"}
		//	|	}).play();
		if(!args.easing){ args.easing = d._defaultEasing; }
		var anim = new d._Animation(args), shape = args.shape, font;
		d.connect(anim, "beforeBegin", anim, function(){
			font = shape.getFont();
			var prop = args.style, values = {}, value, start, end;
			if(prop && prop.values){
				values.style = new InterpolValues(prop.values);
			}
			prop = args.variant;
			if(prop && prop.values){
				values.variant = new InterpolValues(prop.values);
			}
			prop = args.weight;
			if(prop && prop.values){
				values.weight = new InterpolValues(prop.values);
			}
			prop = args.family;
			if(prop && prop.values){
				values.family = new InterpolValues(prop.values);
			}
			prop = args.size;
			if(prop && prop.unit){
				start = parseFloat(prop.start ? prop.start : (shape.font && shape.font.size || "0"));
				end = parseFloat(prop.end ? prop.end : (shape.font && shape.font.size || "0"));
				values.size = new InterpolUnit(start, end, prop.unit);
			}
			this.curve = new InterpolObject(values, font);
		});
		d.connect(anim, "onAnimate", shape, "setFont");
		return anim; // dojo._Animation
	};

	g.fx.animateTransform = function(/*Object*/ args){
		// summary:
		//	Returns an animation which will change transformation over time
		// example:
		//	|	dojox.gfx.fx.animateTransform{{
		//	|		shape: shape,
		//	|		duration: 500,
		//	|		transform: [
		//	|			{name: "translate", start: [0, 0], end: [200, 200]},
		//	|			{name: "original"}
		//	|		]
		//	|	}).play();
		if(!args.easing){ args.easing = d._defaultEasing; }
		var anim = new d._Animation(args), shape = args.shape, original;
		d.connect(anim, "beforeBegin", anim, function(){
			original = shape.getTransform();
			this.curve = new InterpolTransform(args.transform, original);
		});
		d.connect(anim, "onAnimate", shape, "setTransform");
		return anim; // dojo._Animation
	};
})();

}

if(!dojo._hasResource["dojox.charting.action2d.Base"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.action2d.Base"] = true;
dojo.provide("dojox.charting.action2d.Base");





(function(){
	var DEFAULT_DURATION = 400,	// ms
		DEFAULT_EASING   = dojo.fx.easing.backOut,
		df = dojox.lang.functional;

	dojo.declare("dojox.charting.action2d.Base", null, {

		overOutEvents: {onmouseover: 1, onmouseout: 1},

		constructor: function(chart, plot, kwargs){
			this.chart = chart;
			this.plot = plot ? plot : "default";
			this.anim = {};

			// process common optional named parameters
			if(!kwargs){ kwargs = {}; }
			this.duration = kwargs.duration ? kwargs.duration : DEFAULT_DURATION;
			this.easing   = kwargs.easing   ? kwargs.easing   : DEFAULT_EASING;
		},

		connect: function(){
			this.handle = this.chart.connectToPlot(this.plot, this, "process");
		},

		disconnect: function(){
			if(this.handle){
				dojo.disconnect(this.handle);
				this.handle = null;
			}
		},

		reset: function(){
			// nothing by default
		},

		destroy: function(){
			if(this.handle){
				this.disconnect();
			}
			df.forIn(this.anim, function(o){
				df.forIn(o, function(anim){
					anim.action.stop(true);
				});
			});
			this.anim = {};
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.action2d.Highlight"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.action2d.Highlight"] = true;
dojo.provide("dojox.charting.action2d.Highlight");




(function(){
	var DEFAULT_SATURATION  = 100,	// %
		DEFAULT_LUMINOSITY1 = 75,	// %
		DEFAULT_LUMINOSITY2 = 50,	// %

		c = dojox.color,
		
		cc = function(color){
			return function(){ return color; };
		},
		
		hl = function(color){
			var a = new c.Color(color),
				x = a.toHsl();
			if(x.s == 0){
				x.l = x.l < 50 ? 100 : 0;
			}else{
				x.s = DEFAULT_SATURATION;
				if(x.l < DEFAULT_LUMINOSITY2){
					x.l = DEFAULT_LUMINOSITY1;
				}else if(x.l > DEFAULT_LUMINOSITY1){
					x.l = DEFAULT_LUMINOSITY2;
				}else{
					x.l = x.l - DEFAULT_LUMINOSITY2 > DEFAULT_LUMINOSITY1 - x.l ?
						DEFAULT_LUMINOSITY2 : DEFAULT_LUMINOSITY1;
				}
			}
			return c.fromHsl(x);
		};
	
	dojo.declare("dojox.charting.action2d.Highlight", dojox.charting.action2d.Base, {
		// the data description block for the widget parser
		defaultParams: {
			duration: 400,	// duration of the action in ms
			easing:   dojo.fx.easing.backOut	// easing for the action
		},
		optionalParams: {
			highlight: "red"	// name for the highlight color
								// programmatic instantiation can use functions and color objects
		},
		
		constructor: function(chart, plot, kwArgs){
			// process optional named parameters
			var a = kwArgs && kwArgs.highlight;
			this.colorFun = a ? (dojo.isFunction(a) ? a : cc(a)) : hl;
			
			this.connect();
		},
		
		process: function(o){
			if(!o.shape || !(o.type in this.overOutEvents)){ return; }
			
			var runName = o.run.name, index = o.index, anim, startFill, endFill;
	
			if(runName in this.anim){
				anim = this.anim[runName][index];
			}else{
				this.anim[runName] = {};
			}
			
			if(anim){
				anim.action.stop(true);
			}else{
				var color = o.shape.getFill();
				if(!color || !(color instanceof dojo.Color)){
					return;
				}
				this.anim[runName][index] = anim = {
					start: color,
					end:   this.colorFun(color)
				};
			}
			
			var start = anim.start, end = anim.end;
			if(o.type == "onmouseout"){
				// swap colors
				var t = start;
				start = end;
				end = t;
			}
			
			anim.action = dojox.gfx.fx.animateFill({
				shape:    o.shape,
				duration: this.duration,
				easing:   this.easing,
				color:    {start: start, end: end}
			});
			if(o.type == "onmouseout"){
				dojo.connect(anim.action, "onEnd", this, function(){
					if(this.anim[runName]){
						delete this.anim[runName][index];
					}
				});
			}
			anim.action.play();
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.action2d.Magnify"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.action2d.Magnify"] = true;
dojo.provide("dojox.charting.action2d.Magnify");




(function(){
	var DEFAULT_SCALE = 2,
		m = dojox.gfx.matrix,
		gf = dojox.gfx.fx;
	
	dojo.declare("dojox.charting.action2d.Magnify", dojox.charting.action2d.Base, {
		// the data description block for the widget parser
		defaultParams: {
			duration: 400,	// duration of the action in ms
			easing:   dojo.fx.easing.backOut,	// easing for the action
			scale:    DEFAULT_SCALE	// scale of magnification
		},
		optionalParams: {},	// no optional parameters
		
		constructor: function(chart, plot, kwArgs){
			// process optional named parameters
			this.scale = kwArgs && typeof kwArgs.scale == "number" ? kwArgs.scale : DEFAULT_SCALE;
			
			this.connect();
		},
		
		process: function(o){
			if(!o.shape || !(o.type in this.overOutEvents) || 
				!("cx" in o) || !("cy" in o)){ return; }
			
			var runName = o.run.name, index = o.index, vector = [], anim, init, scale;
	
			if(runName in this.anim){
				anim = this.anim[runName][index];
			}else{
				this.anim[runName] = {};
			}
			
			if(anim){
				anim.action.stop(true);
			}else{
				this.anim[runName][index] = anim = {};
			}
			
			if(o.type == "onmouseover"){
				init  = m.identity;
				scale = this.scale;
			}else{
				init  = m.scaleAt(this.scale, o.cx, o.cy);
				scale = 1 / this.scale;
			}
			
			var kwArgs = {
				shape:    o.shape,
				duration: this.duration,
				easing:   this.easing,
				transform: [
					{name: "scaleAt", start: [1, o.cx, o.cy], end: [scale, o.cx, o.cy]},
					init
				]
			};
			if(o.shape){
				vector.push(gf.animateTransform(kwArgs));
			}
			if(o.oultine){
				kwArgs.shape = o.outline;
				vector.push(gf.animateTransform(kwArgs));
			}
			if(o.shadow){
				kwArgs.shape = o.shadow;
				vector.push(gf.animateTransform(kwArgs));
			}
			
			if(!vector.length){
				delete this.anim[runName][index];
				return;
			}
			
			anim.action = dojo.fx.combine(vector);
			if(o.type == "onmouseout"){
				dojo.connect(anim.action, "onEnd", this, function(){
					if(this.anim[runName]){
						delete this.anim[runName][index];
					}
				});
			}
			anim.action.play();
		}
	});
})();

}

if(!dojo._hasResource["dojox.lang.functional.scan"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.lang.functional.scan"] = true;
dojo.provide("dojox.lang.functional.scan");



// This module adds high-level functions and related constructs:
//	- "scan" family of functions

// Notes:
//	- missing high-level functions are provided with the compatible API: 
//		scanl, scanl1, scanr, scanr1

// Defined methods:
//	- take any valid lambda argument as the functional argument
//	- operate on dense arrays
//	- take a string as the array argument
//	- take an iterator objects as the array argument (only scanl, and scanl1)

(function(){
	var d = dojo, df = dojox.lang.functional, empty = {};

	d.mixin(df, {
		// classic reduce-class functions
		scanl: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object*/ z, /*Object?*/ o){
			// summary: repeatedly applies a binary function to an array from left 
			//	to right using a seed value as a starting point; returns an array
			//	of values produced by foldl() at that point.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var t, n, i;
			if(d.isArray(a)){
				// array
				t = new Array((n = a.length) + 1);
				t[0] = z;
				for(i = 0; i < n; z = f.call(o, z, a[i], i, a), t[++i] = z);
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				t = [z];
				for(i = 0; a.hasNext(); t.push(z = f.call(o, z, a.next(), i++, a)));
			}else{
				// object/dictionary
				t = [z];
				for(i in a){
					if(!(i in empty)){
						t.push(z = f.call(o, z, a[i], i, a));
					}
				}
			}
			return t;	// Array
		},
		scanl1: function(/*Array|String|Object*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: repeatedly applies a binary function to an array from left 
			//	to right; returns an array of values produced by foldl1() at that 
			//	point.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var t, n, z, first = true;
			if(d.isArray(a)){
				// array
				t = new Array(n = a.length);
				t[0] = z = a[0];
				for(var i = 1; i < n; t[i] = z = f.call(o, z, a[i], i, a), ++i);
			}else if(typeof a.hasNext == "function" && typeof a.next == "function"){
				// iterator
				if(a.hasNext()){
					t = [z = a.next()];
					for(var i = 1; a.hasNext(); t.push(z = f.call(o, z, a.next(), i++, a)));
				}
			}else{
				// object/dictionary
				for(var i in a){
					if(!(i in empty)){
						if(first){
							t = [z = a[i]];
							first = false;
						}else{
							t.push(z = f.call(o, z, a[i], i, a));
						}
					}
				}
			}
			return t;	// Array
		},
		scanr: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object*/ z, /*Object?*/ o){
			// summary: repeatedly applies a binary function to an array from right
			//	to left using a seed value as a starting point; returns an array
			//	of values produced by foldr() at that point.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var n = a.length, t = new Array(n + 1), i = n;
			t[n] = z;
			for(; i > 0; --i, z = f.call(o, z, a[i], i, a), t[i] = z);
			return t;	// Array
		},
		scanr1: function(/*Array|String*/ a, /*Function|String|Array*/ f, /*Object?*/ o){
			// summary: repeatedly applies a binary function to an array from right
			//	to left; returns an array of values produced by foldr1() at that 
			//	point.
			if(typeof a == "string"){ a = a.split(""); }
			o = o || d.global; f = df.lambda(f);
			var n = a.length, t = new Array(n), z = a[n - 1], i = n - 1;
			t[i] = z;
			for(; i > 0; --i, z = f.call(o, z, a[i], i, a), t[i] = z);
			return t;	// Array
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.action2d.MoveSlice"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.action2d.MoveSlice"] = true;
dojo.provide("dojox.charting.action2d.MoveSlice");








(function(){
	var DEFAULT_SCALE = 1.05,
		DEFAULT_SHIFT = 7,	// px
		m = dojox.gfx.matrix,
		gf = dojox.gfx.fx,
		df = dojox.lang.functional;
	
	dojo.declare("dojox.charting.action2d.MoveSlice", dojox.charting.action2d.Base, {
		// the data description block for the widget parser
		defaultParams: {
			duration: 400,	// duration of the action in ms
			easing:   dojo.fx.easing.backOut,	// easing for the action
			scale:    DEFAULT_SCALE,	// scale of magnification
			shift:    DEFAULT_SHIFT		// shift of the slice
		},
		optionalParams: {},	// no optional parameters

		constructor: function(chart, plot, kwArgs){
			// process optional named parameters
			if(!kwArgs){ kwArgs = {}; }
			this.scale = typeof kwArgs.scale == "number" ? kwArgs.scale : DEFAULT_SCALE;
			this.shift = typeof kwArgs.shift == "number" ? kwArgs.shift : DEFAULT_SHIFT;
			
			this.connect();
		},
		
		process: function(o){
			if(!o.shape || o.element != "slice" || !(o.type in this.overOutEvents)){ return; }
			
			if(!this.angles){
				// calculate the running total of slice angles
				if(typeof o.run.data[0] == "number"){
					this.angles = df.map(df.scanl(o.run.data, "+", 0),
						"* 2 * Math.PI / this", df.foldl(o.run.data, "+", 0));
				}else{
					this.angles = df.map(df.scanl(o.run.data, "a + b.y", 0),
						"* 2 * Math.PI / this", df.foldl(o.run.data, "a + b.y", 0));
				}
			}

			var index = o.index, anim, endScale, startOffset, endOffset,
				angle = (this.angles[index] + this.angles[index + 1]) / 2,
				rotateTo0  = m.rotateAt(-angle, o.cx, o.cy),
				rotateBack = m.rotateAt( angle, o.cx, o.cy);
	
			anim = this.anim[index];
			
			if(anim){
				anim.action.stop(true);
			}else{
				this.anim[index] = anim = {};
			}
			
			if(o.type == "onmouseover"){
				startOffset = 0;
				endOffset   = this.shift;
				endScale    = this.scale;
			}else{
				startOffset = this.shift;
				endOffset   = 0;
				endScale    = 1 / this.scale;
			}
			
			anim.action = dojox.gfx.fx.animateTransform({
				shape:    o.shape,
				duration: this.duration,
				easing:   this.easing,
				transform: [
					rotateBack,
					{name: "translate", start: [startOffset, 0], end: [endOffset, 0]},
					{name: "scaleAt",   start: [1, o.cx, o.cy],  end: [endScale, o.cx, o.cy]},
					rotateTo0
				]
			});

			if(o.type == "onmouseout"){
				dojo.connect(anim.action, "onEnd", this, function(){
					delete this.anim[index];
				});
			}
			anim.action.play();
		},
		
		reset: function(){
			delete this.angles;
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.action2d.Shake"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.action2d.Shake"] = true;
dojo.provide("dojox.charting.action2d.Shake");




(function(){
	var DEFAULT_SHIFT = 3,
		m = dojox.gfx.matrix,
		gf = dojox.gfx.fx;
	
	dojo.declare("dojox.charting.action2d.Shake", dojox.charting.action2d.Base, {
		// the data description block for the widget parser
		defaultParams: {
			duration: 400,	// duration of the action in ms
			easing:   dojo.fx.easing.backOut,	// easing for the action
			shiftX:   DEFAULT_SHIFT,	// shift of the element along the X axis
			shiftY:   DEFAULT_SHIFT		// shift of the element along the Y axis
		},
		optionalParams: {},	// no optional parameters

		constructor: function(chart, plot, kwArgs){
			// process optional named parameters
			if(!kwArgs){ kwArgs = {}; }
			this.shiftX = typeof kwArgs.shiftX == "number" ? kwArgs.shiftX : DEFAULT_SHIFT;
			this.shiftY = typeof kwArgs.shiftY == "number" ? kwArgs.shiftY : DEFAULT_SHIFT;
			
			this.connect();
		},
		
		process: function(o){
			if(!o.shape || !(o.type in this.overOutEvents)){ return; }
			
			var runName = o.run.name, index = o.index, vector = [], anim, 
				shiftX = o.type == "onmouseover" ? this.shiftX : -this.shiftX,
				shiftY = o.type == "onmouseover" ? this.shiftY : -this.shiftY;
	
			if(runName in this.anim){
				anim = this.anim[runName][index];
			}else{
				this.anim[runName] = {};
			}
			
			if(anim){
				anim.action.stop(true);
			}else{
				this.anim[runName][index] = anim = {};
			}
			
			var kwArgs = {
				shape:     o.shape,
				duration:  this.duration,
				easing:    this.easing,
				transform: [
					{name: "translate", start: [this.shiftX, this.shiftY], end: [0, 0]},
					m.identity
				]
			};
			if(o.shape){
				vector.push(gf.animateTransform(kwArgs));
			}
			if(o.oultine){
				kwArgs.shape = o.outline;
				vector.push(gf.animateTransform(kwArgs));
			}
			if(o.shadow){
				kwArgs.shape = o.shadow;
				vector.push(gf.animateTransform(kwArgs));
			}
			
			if(!vector.length){
				delete this.anim[runName][index];
				return;
			}
			
			anim.action = dojo.fx.combine(vector);
			if(o.type == "onmouseout"){
				dojo.connect(anim.action, "onEnd", this, function(){
					if(this.anim[runName]){
						delete this.anim[runName][index];
					}
				});
			}
			anim.action.play();
		}
	});
})();

}

if(!dojo._hasResource["dijit.Tooltip"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dijit.Tooltip"] = true;
dojo.provide("dijit.Tooltip");




dojo.declare(
	"dijit._MasterTooltip",
	[dijit._Widget, dijit._Templated],
	{
		// summary:
		//		Internal widget that holds the actual tooltip markup,
		//		which occurs once per page.
		//		Called by Tooltip widgets which are just containers to hold
		//		the markup
		// tags:
		//		protected

		// duration: Integer
		//		Milliseconds to fade in/fade out
		duration: dijit.defaultDuration,

		templateString:"<div class=\"dijitTooltip dijitTooltipLeft\" id=\"dojoTooltip\">\n\t<div class=\"dijitTooltipContainer dijitTooltipContents\" dojoAttachPoint=\"containerNode\" waiRole='alert'></div>\n\t<div class=\"dijitTooltipConnector\"></div>\n</div>\n",

		postCreate: function(){
			dojo.body().appendChild(this.domNode);

			this.bgIframe = new dijit.BackgroundIframe(this.domNode);

			// Setup fade-in and fade-out functions.
			this.fadeIn = dojo.fadeIn({ node: this.domNode, duration: this.duration, onEnd: dojo.hitch(this, "_onShow") });
			this.fadeOut = dojo.fadeOut({ node: this.domNode, duration: this.duration, onEnd: dojo.hitch(this, "_onHide") });

		},

		show: function(/*String*/ innerHTML, /*DomNode*/ aroundNode, /*String[]?*/ position){
			// summary:
			//		Display tooltip w/specified contents to right of specified node
			//		(To left if there's no space on the right, or if LTR==right)

			if(this.aroundNode && this.aroundNode === aroundNode){
				return;
			}

			if(this.fadeOut.status() == "playing"){
				// previous tooltip is being hidden; wait until the hide completes then show new one
				this._onDeck=arguments;
				return;
			}
			this.containerNode.innerHTML=innerHTML;

			// Firefox bug. when innerHTML changes to be shorter than previous
			// one, the node size will not be updated until it moves.
			this.domNode.style.top = (this.domNode.offsetTop + 1) + "px";

			// position the element and change CSS according to position[] (a list of positions to try)
			var align = {};
			var ltr = this.isLeftToRight();
			dojo.forEach( (position && position.length) ? position : dijit.Tooltip.defaultPosition, function(pos){
				switch(pos){
					case "after":				
						align[ltr ? "BR" : "BL"] = ltr ? "BL" : "BR";
						break;
					case "before":
						align[ltr ? "BL" : "BR"] = ltr ? "BR" : "BL";
						break;
					case "below":
						// first try to align left borders, next try to align right borders (or reverse for RTL mode)
						align[ltr ? "BL" : "BR"] = ltr ? "TL" : "TR";
						align[ltr ? "BR" : "BL"] = ltr ? "TR" : "TL";
						break;
					case "above":
					default:
						// first try to align left borders, next try to align right borders (or reverse for RTL mode)
						align[ltr ? "TL" : "TR"] = ltr ? "BL" : "BR";
						align[ltr ? "TR" : "TL"] = ltr ? "BR" : "BL";
						break;
				}
			});
			var pos = dijit.placeOnScreenAroundElement(this.domNode, aroundNode, align, dojo.hitch(this, "orient"));

			// show it
			dojo.style(this.domNode, "opacity", 0);
			this.fadeIn.play();
			this.isShowingNow = true;
			this.aroundNode = aroundNode;
		},

		orient: function(/* DomNode */ node, /* String */ aroundCorner, /* String */ tooltipCorner){
			// summary:
			//		Private function to set CSS for tooltip node based on which position it's in.
			//		This is called by the dijit popup code.
			// tags:
			//		protected

			node.className = "dijitTooltip " +
				{
					"BL-TL": "dijitTooltipBelow dijitTooltipABLeft",
					"TL-BL": "dijitTooltipAbove dijitTooltipABLeft",
					"BR-TR": "dijitTooltipBelow dijitTooltipABRight",
					"TR-BR": "dijitTooltipAbove dijitTooltipABRight",
					"BR-BL": "dijitTooltipRight",
					"BL-BR": "dijitTooltipLeft"
				}[aroundCorner + "-" + tooltipCorner];
		},

		_onShow: function(){
			// summary:
			//		Called at end of fade-in operation
			// tags:
			//		protected
			if(dojo.isIE){
				// the arrow won't show up on a node w/an opacity filter
				this.domNode.style.filter="";
			}
		},

		hide: function(aroundNode){
			// summary:
			//		Hide the tooltip
			if(this._onDeck && this._onDeck[1] == aroundNode){
				// this hide request is for a show() that hasn't even started yet;
				// just cancel the pending show()
				this._onDeck=null;
			}else if(this.aroundNode === aroundNode){
				// this hide request is for the currently displayed tooltip
				this.fadeIn.stop();
				this.isShowingNow = false;
				this.aroundNode = null;
				this.fadeOut.play();
			}else{
				// just ignore the call, it's for a tooltip that has already been erased
			}
		},

		_onHide: function(){
			// summary:
			//		Called at end of fade-out operation
			// tags:
			//		protected

			this.domNode.style.cssText="";	// to position offscreen again
			if(this._onDeck){
				// a show request has been queued up; do it now
				this.show.apply(this, this._onDeck);
				this._onDeck=null;
			}
		}

	}
);

dijit.showTooltip = function(/*String*/ innerHTML, /*DomNode*/ aroundNode, /*String[]?*/ position){
	// summary:
	//		Display tooltip w/specified contents in specified position.
	//		See description of dijit.Tooltip.defaultPosition for details on position parameter.
	//		If position is not specified then dijit.Tooltip.defaultPosition is used.
	if(!dijit._masterTT){ dijit._masterTT = new dijit._MasterTooltip(); }
	return dijit._masterTT.show(innerHTML, aroundNode, position);
};

dijit.hideTooltip = function(aroundNode){
	// summary:
	//		Hide the tooltip
	if(!dijit._masterTT){ dijit._masterTT = new dijit._MasterTooltip(); }
	return dijit._masterTT.hide(aroundNode);
};

dojo.declare(
	"dijit.Tooltip",
	dijit._Widget,
	{
		// summary
		//		Pops up a tooltip (a help message) when you hover over a node.

		// label: String
		//		Text to display in the tooltip.
		//		Specified as innerHTML when creating the widget from markup.
		label: "",

		// showDelay: Integer
		//		Number of milliseconds to wait after hovering over/focusing on the object, before
		//		the tooltip is displayed.
		showDelay: 400,

		// connectId: [const] String[]
		//		Id's of domNodes to attach the tooltip to.
		//		When user hovers over any of the specified dom nodes, the tooltip will appear.
		//
		//		Note: Currently connectId can only be specified on initialization, it cannot
		//		be changed via attr('connectId', ...)
		//
		//		Note: in 2.0 this will be renamed to connectIds for less confusion.
		connectId: [],

		// position: String[]
		//		See description of `dijit.Tooltip.defaultPosition` for details on position parameter.
		position: [],

		_setConnectIdAttr: function(ids){
			// TODO: erase old conections

			this._connectNodes = [];

			// TODO: rename connectId to connectIds for 2.0, and remove this code converting from string to array
			this.connectId = dojo.isArrayLike(ids) ? ids : [ids];
			
			dojo.forEach(this.connectId, function(id) {
				var node = dojo.byId(id);
				if (node) {
					this._connectNodes.push(node);
					dojo.forEach(["onMouseEnter", "onMouseLeave", "onFocus", "onBlur"], function(event){
						this.connect(node, event.toLowerCase(), "_"+event);
					}, this);
					if(dojo.isIE){
						// BiDi workaround
						node.style.zoom = 1;
					}
				}
			}, this);
		},

		postCreate: function(){	
			dojo.addClass(this.domNode,"dijitTooltipData");
		},

		_onMouseEnter: function(/*Event*/ e){
			// summary:
			//		Handler for mouseenter event on the target node
			// tags:
			//		private
			this._onHover(e);
		},

		_onMouseLeave: function(/*Event*/ e){
			// summary:
			//		Handler for mouseleave event on the target node
			// tags:
			//		private
			this._onUnHover(e);
		},

		_onFocus: function(/*Event*/ e){
			// summary:
			//		Handler for focus event on the target node
			// tags:
			//		private

			// TODO: this is dangerously named, as the dijit focus manager calls
			// _onFocus() on any widget that gets focus (whereas in this class we
			// are connecting onfocus on the *target* DOM node to this method

			this._focus = true;
			this._onHover(e);
			this.inherited(arguments);
		},
		
		_onBlur: function(/*Event*/ e){
			// summary:
			//		Handler for blur event on the target node
			// tags:
			//		private

			// TODO: rename; see above comment

			this._focus = false;
			this._onUnHover(e);
			this.inherited(arguments);
		},

		_onHover: function(/*Event*/ e){
			// summary:
			//		Despite the name of this method, it actually handles both hover and focus
			//		events on the target node, setting a timer to show the tooltip.
			// tags:
			//		private
			if(!this._showTimer){
				var target = e.target;
				this._showTimer = setTimeout(dojo.hitch(this, function(){this.open(target)}), this.showDelay);
			}
		},

		_onUnHover: function(/*Event*/ e){
			// summary:
			//		Despite the name of this method, it actually handles both mouseleave and blur
			//		events on the target node, hiding the tooltip.
			// tags:
			//		private

			// keep a tooltip open if the associated element still has focus (even though the
			// mouse moved away)
			if(this._focus){ return; }

			if(this._showTimer){
				clearTimeout(this._showTimer);
				delete this._showTimer;
			}
			this.close();
		},

		open: function(/*DomNode*/ target){
 			// summary:
			//		Display the tooltip; usually not called directly.
			// tags:
			//		private

			target = target || this._connectNodes[0];
			if(!target){ return; }

			if(this._showTimer){
				clearTimeout(this._showTimer);
				delete this._showTimer;
			}
			dijit.showTooltip(this.label || this.domNode.innerHTML, target, this.position);
			
			this._connectNode = target;
		},

		close: function(){
			// summary:
			//		Hide the tooltip or cancel timer for show of tooltip
			// tags:
			//		private

			if(this._connectNode){
				// if tooltip is currently shown
				dijit.hideTooltip(this._connectNode);
				delete this._connectNode;
			}
			if(this._showTimer){
				// if tooltip is scheduled to be shown (after a brief delay)
				clearTimeout(this._showTimer);
				delete this._showTimer;
			}
		},

		uninitialize: function(){
			this.close();
		}
	}
);

// dijit.Tooltip.defaultPosition: String[]
//		This variable controls the position of tooltips, if the position is not specified to
//		the Tooltip widget or *TextBox widget itself.  It's an array of strings with the following values:
//
//			* before: places tooltip to the left of the target node/widget, or to the right in
//			  the case of RTL scripts like Hebrew and Arabic
//			* after: places tooltip to the right of the target node/widget, or to the left in
//			  the case of RTL scripts like Hebrew and Arabic
//			* above: tooltip goes above target node
//			* below: tooltip goes below target node
//
//		The list is positions is tried, in order, until a position is found where the tooltip fits
//		within the viewport.
//
//		Be careful setting this parameter.  A value of "above" may work fine until the user scrolls
//		the screen so that there's no room above the target node.   Nodes with drop downs, like
//		DropDownButton or FilteringSelect, are especially problematic, in that you need to be sure
//		that the drop down and tooltip don't overlap, even when the viewport is scrolled so that there
//		is only room below (or above) the target node, but not both.
dijit.Tooltip.defaultPosition = ["after", "before"];

}

if(!dojo._hasResource["dojox.charting.action2d.Tooltip"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.action2d.Tooltip"] = true;
dojo.provide("dojox.charting.action2d.Tooltip");








(function(){
	var DEFAULT_TEXT = function(o){
		var t = o.run && o.run.data && o.run.data[o.index];
		if(t && typeof t == "object" && t.tooltip){
			return t.tooltip;
		}
		return o.element == "bar" ? o.x : o.y;
	};
	
	var df = dojox.lang.functional, pi4 = Math.PI / 4, pi2 = Math.PI / 2;
	
	dojo.declare("dojox.charting.action2d.Tooltip", dojox.charting.action2d.Base, {
		// the data description block for the widget parser
		defaultParams: {
			text: DEFAULT_TEXT	// the function to produce a tooltip from the object
		},
		optionalParams: {},	// no optional parameters

		constructor: function(chart, plot, kwArgs){
			// process optional named parameters
			this.text = kwArgs && kwArgs.text ? kwArgs.text : DEFAULT_TEXT;
			
			this.connect();
		},
		
		process: function(o){
			if(o.type === "onplotreset" || o.type === "onmouseout"){
				dijit.hideTooltip(this.aroundRect);
				this.aroundRect = null;
				return;
			}
			
			if(!o.shape || o.type !== "onmouseover"){ return; }
			
			// calculate relative coordinates and the position
			var aroundRect = {type: "rect"}, position = ["after", "before"];
			switch(o.element){
				case "marker":
					aroundRect.x = o.cx;
					aroundRect.y = o.cy;
					aroundRect.width = aroundRect.height = 1;
					break;
				case "circle":
					aroundRect.x = o.cx - o.cr;
					aroundRect.y = o.cy - o.cr;
					aroundRect.width = aroundRect.height = 2 * o.cr;
					break;
				case "column":
					position = ["above", "below"];
				case "bar":
					aroundRect = dojo.clone(o.shape.getShape());
					break;
				default:
				//case "slice":
					if(!this.angles){
						// calculate the running total of slice angles
						if(typeof o.run.data[0] == "number"){
							this.angles = df.map(df.scanl(o.run.data, "+", 0),
								"* 2 * Math.PI / this", df.foldl(o.run.data, "+", 0));
						}else{
							this.angles = df.map(df.scanl(o.run.data, "a + b.y", 0),
								"* 2 * Math.PI / this", df.foldl(o.run.data, "a + b.y", 0));
						}
					}
					var angle = (this.angles[o.index] + this.angles[o.index + 1]) / 2;
					aroundRect.x = o.cx + o.cr * Math.cos(angle);
					aroundRect.y = o.cy + o.cr * Math.sin(angle);
					aroundRect.width = aroundRect.height = 1;
					// calculate the position
					if(angle < pi4){
						// do nothing: the position is right
					}else if(angle < pi2 + pi4){
						position = ["below", "above"];
					}else if(angle < Math.PI + pi4){
						position = ["before", "after"];
					}else if(angle < 2 * Math.PI - pi4){
						position = ["above", "below"];
					}
					/*
					else{
						// do nothing: the position is right
					}
					 */
					break;
			}
			
			// adjust relative coordinates to absolute, and remove fractions
			var lt = dojo.coords(this.chart.node, true);
			aroundRect.x += lt.x;
			aroundRect.y += lt.y;
			aroundRect.x = Math.round(aroundRect.x);
			aroundRect.y = Math.round(aroundRect.y);
			aroundRect.width = Math.ceil(aroundRect.width);
			aroundRect.height = Math.ceil(aroundRect.height);
			this.aroundRect = aroundRect;
			
			dijit.showTooltip(this.text(o), this.aroundRect, position);
		}
	});
})();

}

if(!dojo._hasResource["dojox.charting.widget.Chart2D"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.widget.Chart2D"] = true;
dojo.provide("dojox.charting.widget.Chart2D");





// require all actions to support references by name






(function(){
	var collectParams, collectAxisParams, collectPlotParams,
		collectActionParams, collectDataParams,
		notNull = function(o){ return o; },
		df = dojox.lang.functional,
		du = dojox.lang.utils,
		dc = dojox.charting,
		d = dojo;
	
	dojo.declare("dojox.charting.widget.Chart2D", dijit._Widget, {
		// parameters for the markup
		
		// theme for the chart
		theme: null,
		
		// margins for the chart: {l: 10, r: 10, t: 10, b: 10}
		margins: null,
		
		// chart area
		stroke: null,
		fill:   null,
		
		// methods
		
		buildRendering: function(){
			var n = this.domNode = this.srcNodeRef;
			
			// collect chart parameters
			var axes    = d.query("> .axis", n).map(collectAxisParams).filter(notNull),
				plots   = d.query("> .plot", n).map(collectPlotParams).filter(notNull),
				actions = d.query("> .action", n).map(collectActionParams).filter(notNull),
				series  = d.query("> .series", n).map(collectDataParams).filter(notNull);
			
			// build the chart
			n.innerHTML = "";
			var c = this.chart = new dc.Chart2D(n, {
				margins: this.margins, 
				stroke:  this.stroke,
				fill:    this.fill
			});
			
			// add collected parameters
			if(this.theme){
				c.setTheme(this.theme);
			}
			axes.forEach(function(axis){
				c.addAxis(axis.name, axis.kwArgs);
			});
			plots.forEach(function(plot){
				c.addPlot(plot.name, plot.kwArgs);
			});
			
			this.actions = actions.map(function(action){
				return new action.action(c, action.plot, action.kwArgs)
			});
			
			var render = df.foldl(series, function(render, series){
				if(series.type == "data"){
					c.addSeries(series.name, series.data, series.kwArgs);
					render = true;
				}else{
					c.addSeries(series.name, [0], series.kwArgs);
					var kw = {};
					du.updateWithPattern(
						kw, 
						series.kwArgs, 
						{
							"query": "", 
							"queryOptions": null, 
							"start": 0, 
							"count": 1 //, 
							// "sort": []
						}, 
						true
					);
					if(series.kwArgs.sort){
						// sort is a complex object type and doesn't survive coercian
						kw.sort = dojo.clone(series.kwArgs.sort);
					}
					d.mixin(kw, {
						onComplete: function(data){
							var values;
							if("valueFn" in series.kwArgs){
								var fn = series.kwArgs.valueFn;
								values = d.map(data, function(x){
									return fn(series.data.getValue(x, series.field, 0));
								});
							}else{
								values = d.map(data, function(x){
									return series.data.getValue(x, series.field, 0);
								});
							}
							c.addSeries(series.name, values, series.kwArgs).render();
						}
					});
					series.data.fetch(kw);
				}
				return render;
			}, false);
			if(render){ c.render(); }
		},
		destroy: function(){
			// summary: properly destroy the widget
			this.chart.destroy();
			this.inherited(arguments);
		},
		resize: function(box){
			// summary: resize the widget
			if(box.w > 0 && box.h > 0){
				dojo.marginBox(this.domNode, box);
				this.chart.resize();
			}
		}
	});
	
	collectParams = function(node, type, kw){
		var dp = eval("(" + type + ".prototype.defaultParams)");
		var x, attr;
		for(x in dp){
			if(x in kw){ continue; }
			attr = node.getAttribute(x);
			kw[x] = du.coerceType(dp[x], attr == null || typeof attr == "undefined" ? dp[x] : attr);
		}
		var op = eval("(" + type + ".prototype.optionalParams)");
		for(x in op){
			if(x in kw){ continue; }
			attr = node.getAttribute(x);
			if(attr != null){
				kw[x] = du.coerceType(op[x], attr);
			}
		}
	};
	
	collectAxisParams = function(node){
		var name = node.getAttribute("name"), type = node.getAttribute("type");
		if(!name){ return null; }
		var o = {name: name, kwArgs: {}}, kw = o.kwArgs;
		if(type){
			if(dc.axis2d[type]){
				type = dojox._scopeName + ".charting.axis2d." + type;
			}
			var axis = eval("(" + type + ")");
			if(axis){ kw.type = axis; } 
		}else{
			type = dojox._scopeName + ".charting.axis2d.Default";
		}
		collectParams(node, type, kw);
		return o;
	};
	
	collectPlotParams = function(node){
		// var name = d.attr(node, "name"), type = d.attr(node, "type");
		var name = node.getAttribute("name"), type = node.getAttribute("type");
		if(!name){ return null; }
		var o = {name: name, kwArgs: {}}, kw = o.kwArgs;
		if(type){
			if(dc.plot2d[type]){
				type = dojox._scopeName + ".charting.plot2d." + type;
			}
			var plot = eval("(" + type + ")");
			if(plot){ kw.type = plot; } 
		}else{
			type = dojox._scopeName + ".charting.plot2d.Default";
		}
		collectParams(node, type, kw);
		return o;
	};
	
	collectActionParams = function(node){
		// var plot = d.attr(node, "plot"), type = d.attr(node, "type");
		var plot = node.getAttribute("plot"), type = node.getAttribute("type");
		if(!plot){ plot = "default"; }
		var o = {plot: plot, kwArgs: {}}, kw = o.kwArgs;
		if(type){
			if(dc.action2d[type]){
				type = dojox._scopeName + ".charting.action2d." + type;
			}
			var action = eval("(" + type + ")");
			if(!action){ return null; }
			o.action = action;
		}else{
			return null;
		}
		collectParams(node, type, kw);
		return o;
	};

	collectDataParams = function(node){
		var ga = d.partial(d.attr, node);
		var name = ga("name");
		if(!name){ return null; }
		var o = { name: name, kwArgs: {} }, kw = o.kwArgs, t;
		t = ga("plot");
		if(t != null){ kw.plot = t; }
		t = ga("marker");
		if(t != null){ kw.marker = t; }
		t = ga("stroke");
		if(t != null){ kw.stroke = eval("(" + t + ")"); }
		t = ga("fill");
		if(t != null){ kw.fill = eval("(" + t + ")"); }
		t = ga("legend");
		if(t != null){ kw.legend = t; }
		t = ga("data");
		if(t != null){
			o.type = "data";
			o.data = dojo.map(String(t).split(','), Number);
			return o;
		}
		t = ga("array");
		if(t != null){
			o.type = "data";
			o.data = eval("(" + t + ")");
			return o;
		}
		t = ga("store");
		if(t != null){
			o.type = "store";
			o.data = eval("(" + t + ")");
			t = ga("field");
			o.field = t != null ? t : "value";
			t = ga("query");
			if(!!t){ kw.query = t; }
			t = ga("queryOptions");
			if(!!t){ kw.queryOptions = eval("(" + t + ")"); }
			t = ga("start");
			if(!!t){ kw.start = Number(t); }
			t = ga("count");
			if(!!t){ kw.count = Number(t); }
			t = ga("sort");
			if(!!t){ kw.sort = eval("("+t+")"); }
			t = ga("valueFn");
			if(!!t){ kw.valueFn = df.lambda(t); }
			return o;
		}
		return null;
	};
})();

}

if(!dojo._hasResource["dojox.charting.themes.ET.greys"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.themes.ET.greys"] = true;
dojo.provide("dojox.charting.themes.ET.greys");

dojo.deprecated("dojox.charting.themes.ET.greys", "1.3");

(function(){
	var dxc=dojox.charting;
	dxc.themes.ET.greys = new dxc.Theme({
		antiAlias: false,
		chart: {
			stroke: null,
			fill: "inherit"
		},
		plotarea: {
			// stroke: { width: 0.2, color: "#666666" },
			stroke: null,
			fill: "transparent"
		},
		axis:{
			stroke:{ width:	0 },
			line:{ width:	0 },
			majorTick:{ 
				color:	"#666666", 
				width:	1,
				length: 5
			},
			minorTick: { 
				color:	"black", 
				width:	0.5, 
				length:	2
			},
			font:"normal normal normal 8pt Tahoma",
			fontColor:"#999999"
		},
		series:{
			outline:{ width: 0, color: "black" },
			stroke:	{ width: 1, color: "black" },
			fill:	dojo.colorFromHex("#3b444b"),
			font: "normal normal normal 7pt Tahoma",	//	label
			fontColor: "#717171"
		},
		marker:{	//	any markers on a series.
			stroke:{ width:1 },
			fill:"#333",
			font:"normal normal normal 7pt Tahoma",	//	label
			fontColor:"#000"
		},
		colors:[
			dojo.colorFromHex("#8a8c8f"), 
			dojo.colorFromHex("#4b4b4b"),
			dojo.colorFromHex("#3b444b"), 
			dojo.colorFromHex("#2e2d30"),
			dojo.colorFromHex("#000000") 
		]
	});
})();

}

if(!dojo._hasResource["dojox.charting.widget.Sparkline"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.widget.Sparkline"] = true;
dojo.provide("dojox.charting.widget.Sparkline");




(function(){

	var d = dojo;

	dojo.declare("dojox.charting.widget.Sparkline",
		dojox.charting.widget.Chart2D,
		{
			theme: dojox.charting.themes.ET.greys,
			margins: { l: 0, r: 0, t: 0, b: 0 },
			type: "Lines",
			valueFn: "Number(x)",
			store: "",
			field: "",
			query: "",
			queryOptions: "",
			start: "0",
			count: "Infinity",
			sort: "",
			data: "",
			name: "default",
			buildRendering: function(){
				var n = this.srcNodeRef;
				if(	!n.childNodes.length || // shortcut the query
					!d.query("> .axis, > .plot, > .action, > .series", n).length){
					var plot = document.createElement("div");
					d.attr(plot, {
						"class": "plot",
						"name": "default",
						"type": this.type
					});
					n.appendChild(plot);

					var series = document.createElement("div");
					d.attr(series, {
						"class": "series",
						plot: "default",
						name: this.name,
						start: this.start,
						count: this.count,
						valueFn: this.valueFn
					});
					d.forEach(
						["store", "field", "query", "queryOptions", "sort", "data"],
						function(i){
							if(this[i].length){
								d.attr(series, i, this[i]);
							}
						},
						this
					);
					n.appendChild(series);
				}
				this.inherited(arguments);
			}
		}
	);

})();

}

if(!dojo._hasResource["dojox.charting.widget.Legend"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.charting.widget.Legend"] = true;
dojo.provide("dojox.charting.widget.Legend");







dojo.declare("dojox.charting.widget.Legend", [dijit._Widget, dijit._Templated], {
	// summary: A legend for a chart. A legend contains summary labels for 
	// each series of data contained in the chart.
	//
	// Set the boolean horizontal attribute to false to layout legend labels vertically.
	//
	// (Line or Scatter charts (colored lines with shape symbols) )
	// -o- Series1		-X- Series2		-v- Series3
	//
	// (Area/Bar/Pie charts (letters represent colors))
	// [a] Series1		[b] Series2		[c] Series3
	
	chartRef:   "",
	horizontal: true,
	
	templateString: "<table dojoAttachPoint='legendNode' class='dojoxLegendNode'><tbody dojoAttachPoint='legendBody'></tbody></table>",
	
	legendNode: null,
	legendBody: null,
	
	postCreate: function(){
		if(!this.chart){
			if(!this.chartRef){ return; }
			this.chart = dijit.byId(this.chartRef);
			if(!this.chart){
				var node = dojo.byId(this.chartRef);
				if(node){
					this.chart = dijit.byNode(node);
				}else{
					
					return;
				}
			}
			this.series = this.chart.chart.series;
		}else{
			this.series = this.chart.series;
		}
		
		this.refresh();
	},
	refresh: function(){
		// summary: regenerates the legend to reflect changes to the chart
		
		var df = dojox.lang.functional;

		// cleanup
		if(this._surfaces){
			dojo.forEach(this._surfaces, function(surface){
				surface.destroy();
			});
		}
		this._surfaces = [];
		while(this.legendBody.lastChild){
			dojo.destroy(this.legendBody.lastChild);
		}

		if(this.horizontal){
			dojo.addClass(this.legendNode, "dojoxLegendHorizontal");
			// make a container <tr>
			this._tr = dojo.doc.createElement("tr");
			this.legendBody.appendChild(this._tr);
		}
		
		var s = this.series;
		if(s.length == 0){
			return;
		}
		if(s[0].chart.stack[0].declaredClass == "dojox.charting.plot2d.Pie"){
			var t = s[0].chart.stack[0];
			if(typeof t.run.data[0] == "number"){
				var filteredRun = df.map(t.run.data, "Math.max(x, 0)");
				if(df.every(filteredRun, "<= 0")){
					return;
				}
				var slices = df.map(filteredRun, "/this", df.foldl(filteredRun, "+", 0));
				dojo.forEach(slices, function(x, i){
					this._addLabel(t.dyn[i], t._getLabel(x * 100) + "%");
				}, this);
			}else{
				dojo.forEach(t.run.data, function(x, i){
					this._addLabel(t.dyn[i], x.legend || x.text || x.y);
				}, this);
			}
		}else{
			dojo.forEach(s, function(x){
				this._addLabel(x.dyn, x.legend || x.name);
			}, this);
		}
	},
	_addLabel: function(dyn, label){
		// create necessary elements
		var icon = dojo.doc.createElement("td"),
			text = dojo.doc.createElement("td"),
			div  = dojo.doc.createElement("div");
		dojo.addClass(icon, "dojoxLegendIcon");
		dojo.addClass(text, "dojoxLegendText");
		div.style.width  = "20px";
		div.style.height = "20px";
		icon.appendChild(div);
		
		// create a skeleton
		if(this._tr){
			// horizontal
			this._tr.appendChild(icon);
			this._tr.appendChild(text);
		}else{
			// vertical
			var tr = dojo.doc.createElement("tr");
			this.legendBody.appendChild(tr);
			tr.appendChild(icon);
			tr.appendChild(text);
		}
		
		// populate the skeleton
		this._makeIcon(div, dyn);
		text.innerHTML = String(label);
	},
	_makeIcon: function(div, dyn){
		var mb = {h: 14, w: 14};
		var surface = dojox.gfx.createSurface(div, mb.w, mb.h);
		this._surfaces.push(surface);
		if(dyn.fill){
			// regions
			surface.createRect({x: 2, y: 2, width: mb.w - 4, height: mb.h - 4}).
				setFill(dyn.fill).setStroke(dyn.stroke);
		}else if(dyn.stroke || dyn.marker){
			// draw line
			var line = {x1: 0, y1: mb.h / 2, x2: mb.w, y2: mb.h / 2};
			if(dyn.stroke){
				surface.createLine(line).setStroke(dyn.stroke);
			}
			if(dyn.marker){
				// draw marker on top
				var c = {x: mb.w / 2, y: mb.h / 2};
				if(dyn.stroke){
					surface.createPath({path: "M" + c.x + " " + c.y + " " + dyn.marker}).
						setFill(dyn.stroke.color).setStroke(dyn.stroke);
				}else{
					surface.createPath({path: "M" + c.x + " " + c.y + " " + dyn.marker}).
						setFill(dyn.color).setStroke(dyn.color);
				}
			}
		}else{
			// nothing
			surface.createRect({x: 2, y: 2, width: mb.w - 4, height: mb.h - 4}).
				setStroke("black");
			surface.createLine({x1: 2, y1: 2, x2: mb.w - 2, y2: mb.h - 2}).setStroke("black");
			surface.createLine({x1: 2, y1: mb.h - 2, x2: mb.w - 2, y2: 2}).setStroke("black");
		}
	}
});

}

