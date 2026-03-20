/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Tested with Hawtio 4.x (PatternFly v5) — CSS selectors are coupled to this version's DOM structure.
(() => {
  const diagram = {};

  diagram.prepare = () => {
    const sidebar = document.querySelector('.pf-v5-c-page__sidebar');
    if (sidebar) {
      sidebar.style.display = 'none';
    }
    const header = document.querySelector('.pf-v5-c-page__header');
    if (header) {
      header.style.display = 'none';
    }
    const split = document.querySelector('.camel-split');
    if (split && split.children && split.children.length > 1) {
      split.children[0].style.display = 'none';
      split.children[1].style.width = '100%';
    }
    const outer = document.querySelector('#camel-route-diagram-outer-div');
    const container = outer ? outer.querySelector('.react-flow') : null;
    const layers = [
      '#camel-route-diagram-outer-div .react-flow__renderer',
      '#camel-route-diagram-outer-div .react-flow__pane',
      '#camel-route-diagram-outer-div .react-flow__viewport',
      '#camel-route-diagram-outer-div .react-flow__container',
      '#camel-route-diagram-outer-div .camel-route-diagram'
    ];
    if (outer) {
      outer.style.overflow = 'visible';
    }
    if (container) {
      container.style.overflow = 'visible';
    }
    layers.forEach((selector) => {
      document.querySelectorAll(selector).forEach((element) => {
        element.style.overflow = 'visible';
      });
    });
    document
      .querySelectorAll('.pf-v5-c-scroll-outer-wrapper, .pf-v5-c-scroll-inner-wrapper')
      .forEach((element) => {
        element.style.overflow = 'visible';
        element.style.maxWidth = 'none';
        element.style.maxHeight = 'none';
      });
    const unclip = (element) => {
      let node = element;
      while (node && node !== document.body) {
        node.style.overflow = 'visible';
        node.style.maxWidth = 'none';
        node.style.maxHeight = 'none';
        node = node.parentElement;
      }
    };
    if (outer) {
      unclip(outer);
    }
    const main = document.querySelector('#camel-content-main');
    if (main) {
      main.style.overflow = 'visible';
    }
    document.documentElement.style.overflow = 'visible';
    document.body.style.overflow = 'visible';
  };

  diagram.normalize = () => {
    const outer = document.querySelector('#camel-route-diagram-outer-div');
    const viewport = outer ? outer.querySelector('.react-flow__viewport') : null;
    const nodes = outer ? Array.from(outer.querySelectorAll('.react-flow__node')) : [];
    const edges = outer ? Array.from(outer.querySelectorAll('.react-flow__edge-path')) : [];
    const labels = outer
      ? Array.from(
          outer.querySelectorAll(
            '.react-flow__node *, .react-flow__edge-text, .react-flow__edge-textwrapper'
          )
        )
      : [];
    const renderer = outer ? outer.querySelector('.react-flow__renderer') : null;
    if (!outer || !viewport || (nodes.length === 0 && edges.length === 0)) {
      return;
    }
    const transform = viewport.style.transform || '';
    let scale = 1;
    let tx = 0;
    let ty = 0;
    let match = transform.match(/translate\(([-0-9.]+)px,\s*([-0-9.]+)px\)/);
    if (!match) {
      match = transform.match(/translate3d\(([-0-9.]+)px,\s*([-0-9.]+)px/);
    }
    if (match) {
      tx = parseFloat(match[1]);
      ty = parseFloat(match[2]);
    }
    const scaleMatch = transform.match(/scale\(([-0-9.]+)\)/);
    if (scaleMatch) {
      scale = parseFloat(scaleMatch[1]);
    }
    if (!isFinite(scale) || scale <= 0) {
      scale = 1;
    }
    if (!isFinite(tx)) {
      tx = 0;
    }
    if (!isFinite(ty)) {
      ty = 0;
    }
    const base = renderer || outer;
    const baseRect = base.getBoundingClientRect();
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    const updateBounds = (rect) => {
      const x1 = (rect.left - baseRect.left - tx) / scale;
      const y1 = (rect.top - baseRect.top - ty) / scale;
      const x2 = (rect.right - baseRect.left - tx) / scale;
      const y2 = (rect.bottom - baseRect.top - ty) / scale;
      minX = Math.min(minX, x1);
      minY = Math.min(minY, y1);
      maxX = Math.max(maxX, x2);
      maxY = Math.max(maxY, y2);
    };
    nodes.forEach((node) => updateBounds(node.getBoundingClientRect()));
    edges.forEach((edge) => updateBounds(edge.getBoundingClientRect()));
    labels.forEach((label) => updateBounds(label.getBoundingClientRect()));
    if (!isFinite(minX) || !isFinite(minY) || !isFinite(maxX) || !isFinite(maxY)) {
      return;
    }
    const padding = 24;
    const extraLeft = 48;
    const extraRight = 96;
    const extraTop = 0;
    const extraBottom = 24;
    const leftPadding = padding + extraLeft;
    const rightPadding = padding + extraRight;
    const topPadding = padding + extraTop;
    const bottomPadding = padding + extraBottom;
    const width = Math.ceil((maxX - minX) * scale + leftPadding + rightPadding);
    const height = Math.ceil((maxY - minY) * scale + topPadding + bottomPadding);
    outer.style.width = `${width}px`;
    outer.style.height = `${height}px`;
    outer.style.minWidth = outer.style.width;
    outer.style.minHeight = outer.style.height;
    const container = outer.querySelector('.react-flow');
    if (container) {
      container.style.width = outer.style.width;
      container.style.height = outer.style.height;
    }
    viewport.style.transformOrigin = '0 0';
    viewport.style.transform =
      `translate(${leftPadding - minX * scale}px, ${topPadding - minY * scale}px) ` +
      `scale(${scale})`;
  };

  diagram.computeClip = () => {
    const outer = document.querySelector('#camel-route-diagram-outer-div');
    const viewport = document.querySelector('#camel-route-diagram-outer-div .react-flow__viewport');
    const nodes = Array.from(
      document.querySelectorAll('#camel-route-diagram-outer-div .react-flow__node')
    );
    const edges = Array.from(
      document.querySelectorAll('#camel-route-diagram-outer-div .react-flow__edge-path')
    );
    const labels = Array.from(
      document.querySelectorAll(
        '#camel-route-diagram-outer-div .react-flow__node *, ' +
          '#camel-route-diagram-outer-div .react-flow__edge-text, ' +
          '#camel-route-diagram-outer-div .react-flow__edge-textwrapper'
      )
    );
    if (nodes.length === 0 && edges.length === 0) {
      return null;
    }
    const parseTransform = () => {
      const transform = viewport ? viewport.style.transform || '' : '';
      let tx = 0;
      let ty = 0;
      let scale = 1;
      let match = transform.match(/translate\(([-0-9.]+)px,\s*([-0-9.]+)px\)/);
      if (!match) {
        match = transform.match(/translate3d\(([-0-9.]+)px,\s*([-0-9.]+)px/);
      }
      if (match) {
        tx = parseFloat(match[1]);
        ty = parseFloat(match[2]);
      }
      const scaleMatch = transform.match(/scale\(([-0-9.]+)\)/);
      if (scaleMatch) {
        scale = parseFloat(scaleMatch[1]);
      }
      if (!isFinite(scale) || scale <= 0) {
        scale = 1;
      }
      return { tx, ty, scale };
    };
    const computeBounds = () => {
      let minX = Infinity;
      let minY = Infinity;
      let maxX = -Infinity;
      let maxY = -Infinity;
      const updateBounds = (rect) => {
        minX = Math.min(minX, rect.left);
        minY = Math.min(minY, rect.top);
        maxX = Math.max(maxX, rect.right);
        maxY = Math.max(maxY, rect.bottom);
      };
      const applyBounds = (list) => {
        list.forEach((element) => updateBounds(element.getBoundingClientRect()));
      };
      if (nodes.length > 0) {
        applyBounds(nodes);
      }
      if (labels.length > 0) {
        applyBounds(labels);
      }
      if (edges.length > 0) {
        let edgeMinX = Infinity;
        let edgeMinY = Infinity;
        let edgeMaxX = -Infinity;
        let edgeMaxY = -Infinity;
        edges.forEach((element) => {
          const rect = element.getBoundingClientRect();
          edgeMinX = Math.min(edgeMinX, rect.left);
          edgeMinY = Math.min(edgeMinY, rect.top);
          edgeMaxX = Math.max(edgeMaxX, rect.right);
          edgeMaxY = Math.max(edgeMaxY, rect.bottom);
        });
        if (nodes.length === 0) {
          minX = edgeMinX;
          minY = edgeMinY;
          maxX = edgeMaxX;
          maxY = edgeMaxY;
        } else {
          const edgeSlack = 24;
          if (edgeMinX < minX - edgeSlack) {
            minX = edgeMinX;
          }
          if (edgeMinY < minY - edgeSlack) {
            minY = edgeMinY;
          }
          if (edgeMaxX > maxX + edgeSlack) {
            maxX = edgeMaxX;
          }
          if (edgeMaxY > maxY + edgeSlack) {
            maxY = edgeMaxY;
          }
        }
      }
      if (!isFinite(minX) || !isFinite(minY) || !isFinite(maxX) || !isFinite(maxY)) {
        return null;
      }
      return { minX, minY, maxX, maxY };
    };
    const padding = 24;
    const extraLeft = 48;
    const extraRight = 96;
    const extraTop = 0;
    const extraBottom = 24;
    const leftPadding = padding + extraLeft;
    const rightPadding = padding + extraRight;
    const topPadding = padding + extraTop;
    const bottomPadding = padding + extraBottom;
    let bounds = computeBounds();
    if (!bounds) {
      return null;
    }
    if (viewport) {
      const rect = outer ? outer.getBoundingClientRect() : { left: 0, top: 0 };
      const desiredLeft = rect.left + leftPadding;
      const desiredTop = rect.top + topPadding;
      const deltaX = bounds.minX < desiredLeft ? desiredLeft - bounds.minX : 0;
      const deltaY = bounds.minY < desiredTop ? desiredTop - bounds.minY : 0;
      if (Math.abs(deltaX) > 1 || Math.abs(deltaY) > 1) {
        const transform = parseTransform();
        const tx = transform.tx + deltaX;
        const ty = transform.ty + deltaY;
        viewport.style.transformOrigin = '0 0';
        viewport.style.transform = `translate(${tx}px, ${ty}px) scale(${transform.scale})`;
        bounds = computeBounds();
        if (!bounds) {
          return null;
        }
      }
    }
    const scrollX = window.scrollX || window.pageXOffset || 0;
    const scrollY = window.scrollY || window.pageYOffset || 0;
    const x1 = bounds.minX + scrollX - leftPadding;
    const y1 = bounds.minY + scrollY - topPadding;
    const x2 = bounds.maxX + scrollX + rightPadding;
    const y2 = bounds.maxY + scrollY + bottomPadding;
    const x = Math.max(0, x1);
    const y = Math.max(0, y1);
    const width = Math.max(1, x2 - x1);
    const height = Math.max(1, y2 - y1);
    return { x, y, width, height };
  };

  // Compute a screenshot clip covering only the nodes that belong to a specific route.
  // Hawtio renders all routes sequentially in the diagram — each route's "from" node
  // has the route ID embedded in its text as "(ID: routeId)". Nodes for a route span
  // from that "from" node up to (but not including) the next route's "from" node.
  diagram.computeClipForRoute = (routeId) => {
    const allNodes = Array.from(
      document.querySelectorAll('#camel-route-diagram-outer-div .react-flow__node')
    );
    if (allNodes.length === 0) return null;

    // Find the "from" node for this route — its text contains "(ID: routeId)".
    // In Camel, the from step's ID equals the route ID, making this unambiguous.
    const fromIdx = allNodes.findIndex((n) => {
      const text = n.textContent || '';
      return text.includes('(ID: ' + routeId + ')');
    });
    if (fromIdx < 0) return null;

    // Find the next route's "from" node to bound this route's node range.
    // A "from" node text contains "From " (after optional leading digits/badges)
    // and has "(ID: someId)" where the ID is different from the current routeId.
    let nextFromIdx = allNodes.length;
    for (let i = fromIdx + 1; i < allNodes.length; i++) {
      const text = allNodes[i].textContent || '';
      if (/From\s+\S/.test(text) && text.includes('(ID: ') && !text.includes('(ID: ' + routeId + ')')) {
        nextFromIdx = i;
        break;
      }
    }

    const routeNodes = allNodes.slice(fromIdx, nextFromIdx);
    if (routeNodes.length === 0) return null;

    const scrollX = window.scrollX || window.pageXOffset || 0;
    const scrollY = window.scrollY || window.pageYOffset || 0;
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    routeNodes.forEach((node) => {
      const rect = node.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        minX = Math.min(minX, rect.left + scrollX);
        minY = Math.min(minY, rect.top + scrollY);
        maxX = Math.max(maxX, rect.right + scrollX);
        maxY = Math.max(maxY, rect.bottom + scrollY);
      }
    });
    if (!isFinite(minX) || !isFinite(minY) || maxX <= minX || maxY <= minY) return null;

    const padding = 24;
    return {
      x: Math.max(0, minX - padding),
      y: Math.max(0, minY - padding),
      width: maxX - minX + 2 * padding,
      height: maxY - minY + 2 * padding
    };
  };



  diagram.waitForAssets = async () => {
    if (document.fonts && document.fonts.ready) {
      await document.fonts.ready;
    }
    const images = Array.from(document.querySelectorAll('#camel-route-diagram-outer-div img'));
    await Promise.all(images.map((image) => image.decode().catch(() => {})));
  };

  window.camelDiagram = diagram;

  // CSP-safe state monitor: update HTML data attributes so Java can use locator().waitFor()
  // instead of page.waitForFunction(String) or page.evaluate(String), both of which require
  // eval() and are blocked by Hawtio's Content-Security-Policy when using Chrome.
  (function startStateMonitor() {
    var html = document.documentElement;
    var prepared = false;
    var stableState = null;

    function isStable(nodes) {
      if (!nodes.length) return false;
      var viewport = document.querySelector('.react-flow__viewport');
      var transform = viewport ? viewport.style.transform : '';
      var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      nodes.forEach(function(node) {
        var r = node.getBoundingClientRect();
        minX = Math.min(minX, r.left); minY = Math.min(minY, r.top);
        maxX = Math.max(maxX, r.right); maxY = Math.max(maxY, r.bottom);
      });
      var now = Date.now();
      if (!stableState || stableState.count !== nodes.length || stableState.transform !== transform ||
          Math.round(stableState.minX) !== Math.round(minX) || Math.round(stableState.minY) !== Math.round(minY) ||
          Math.round(stableState.maxX) !== Math.round(maxX) || Math.round(stableState.maxY) !== Math.round(maxY)) {
        stableState = { count: nodes.length, transform: transform, minX: minX, minY: minY, maxX: maxX, maxY: maxY, at: now };
        return false;
      }
      return now - stableState.at > 250;
    }

    function tick() {
      try {
        if (!html.hasAttribute('data-camel-connected') &&
            sessionStorage.getItem('connect.currentConnection') !== null) {
          html.setAttribute('data-camel-connected', 'true');
        }
      } catch (e) { /* ignore */ }

      try {
        var nodes = document.querySelectorAll('.react-flow__node');
        if (!prepared && nodes.length > 0) {
          try { window.camelDiagram.prepare(); } catch (e) { /* ignore */ }
          prepared = true;
        }
        if (isStable(nodes)) {
          html.setAttribute('data-camel-stable', 'true');
        } else {
          html.removeAttribute('data-camel-stable');
        }
      } catch (e) { /* ignore */ }

      setTimeout(tick, 100);
    }

    tick();
  })();
})();
