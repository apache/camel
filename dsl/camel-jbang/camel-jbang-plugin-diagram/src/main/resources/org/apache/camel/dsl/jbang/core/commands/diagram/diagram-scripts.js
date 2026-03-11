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

  diagram.isStable = () => {
    const nodes = Array.from(document.querySelectorAll('.react-flow__node'));
    if (!nodes.length) {
      return false;
    }
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    nodes.forEach((node) => {
      const rect = node.getBoundingClientRect();
      minX = Math.min(minX, rect.left);
      minY = Math.min(minY, rect.top);
      maxX = Math.max(maxX, rect.right);
      maxY = Math.max(maxY, rect.bottom);
    });
    const bounds = {
      minX: Math.round(minX),
      minY: Math.round(minY),
      maxX: Math.round(maxX),
      maxY: Math.round(maxY)
    };
    const viewport = document.querySelector('.react-flow__viewport');
    const transform = viewport ? viewport.style.transform : '';
    const now = Date.now();
    const state =
      window.__camelDiagramState ||
      (window.__camelDiagramState = { count: nodes.length, transform, bounds, at: now });
    const changed =
      state.count !== nodes.length ||
      state.transform !== transform ||
      !state.bounds ||
      state.bounds.minX !== bounds.minX ||
      state.bounds.minY !== bounds.minY ||
      state.bounds.maxX !== bounds.maxX ||
      state.bounds.maxY !== bounds.maxY;
    if (changed) {
      state.count = nodes.length;
      state.transform = transform;
      state.bounds = bounds;
      state.at = now;
      return false;
    }
    return now - state.at > 200;
  };

  diagram.isRouteSelected = (routeId) => {
    return Array.from(document.querySelectorAll('#camel-tree-view [aria-selected="true"]')).some(
      (element) =>
        Array.from(element.querySelectorAll('button.pf-v5-c-tree-view__node-text')).some(
          (button) => button.textContent && button.textContent.trim() === routeId
        )
    );
  };

  diagram.isRoutesFolderSelected = () => {
    return Array.from(document.querySelectorAll('#camel-tree-view [aria-selected="true"]')).some(
      (element) =>
        Array.from(element.querySelectorAll('button.pf-v5-c-tree-view__node-text')).some((button) => {
          return button.textContent && button.textContent.trim().toLowerCase() === 'routes';
        })
    );
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

    function tick() {
      // Track Jolokia connection (set once, never cleared)
      try {
        if (!html.hasAttribute('data-camel-connected') &&
            sessionStorage.getItem('connect.currentConnection') !== null) {
          html.setAttribute('data-camel-connected', 'true');
        }
      } catch (e) { /* ignore */ }

      // Track diagram state
      try {
        if (window.camelDiagram) {
          var nodes = document.querySelectorAll('.react-flow__node');

          // Auto-call prepare() once when diagram nodes first appear
          if (!prepared && nodes.length > 0) {
            try { window.camelDiagram.prepare(); } catch (e) { /* ignore */ }
            prepared = true;
          }

          // Track stable state (set/clear on every tick)
          if (window.camelDiagram.isStable()) {
            html.setAttribute('data-camel-stable', 'true');
          } else {
            html.removeAttribute('data-camel-stable');
          }

          // Track routes-folder selected
          try {
            if (window.camelDiagram.isRoutesFolderSelected()) {
              html.setAttribute('data-camel-routes-folder-selected', 'true');
            } else {
              html.removeAttribute('data-camel-routes-folder-selected');
            }
          } catch (e) { /* ignore */ }
        }
      } catch (e) { /* ignore */ }

      setTimeout(tick, 100);
    }

    tick();
  })();
})();
