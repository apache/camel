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

export const NODE_W = 180;
export const NODE_H = 36;
export const H_GAP = NODE_W / 2;
export const V_GAP = 40;
export const PADDING = 30;

const BRANCHING_EIPS = new Set([
  'choice', 'multicast', 'doTry', 'loadBalance', 'recipientList', 'circuitBreaker',
]);

/**
 * Builds a tree of {info, children, parent, subtreeWidth} nodes from a flat, level-ordered array.
 * Faithfully ports RouteDiagramLayoutEngine.buildTree().
 *
 * @param {Array<{type:string, id:string, level:number, code:string}>} nodes
 * @returns {{info, children, parent, subtreeWidth}|null}
 */
export function buildTree(nodes) {
  if (!nodes.length) return null;
  const root = { info: nodes[0], children: [], parent: null, subtreeWidth: 0 };
  let current = root;

  for (let i = 1; i < nodes.length; i++) {
    const ni = nodes[i];
    // Skip nodes without an id — they would collide on positions[undefined].
    if (!ni.id) continue;
    const tn = { info: ni, children: [], parent: null, subtreeWidth: 0 };

    if (ni.level > current.info.level) {
      current.children.push(tn);
      tn.parent = current;
    } else if (ni.level === current.info.level) {
      const parent = current.parent ?? root;
      parent.children.push(tn);
      tn.parent = parent;
    } else {
      let ancestor = current.parent;
      while (ancestor && ancestor.info.level >= ni.level) {
        ancestor = ancestor.parent;
      }
      const target = ancestor ?? root;
      target.children.push(tn);
      tn.parent = target;
    }
    current = tn;
  }
  return root;
}

/**
 * Computes subtreeWidth on every node (bottom-up).
 * Branching EIPs: sum of child widths + (n-1)*H_GAP.
 * Others: max of child widths.
 *
 * @param {{info, children, subtreeWidth}} node
 * @returns {number}
 */
export function computeSubtreeWidth(node) {
  if (!node.children.length) {
    node.subtreeWidth = NODE_W;
    return NODE_W;
  }
  if (BRANCHING_EIPS.has(node.info.type)) {
    let total = 0;
    node.children.forEach((c, i) => {
      if (i > 0) total += H_GAP;
      total += computeSubtreeWidth(c);
    });
    node.subtreeWidth = Math.max(NODE_W, total);
  } else {
    // Use reduce instead of spread to avoid RangeError on very large child arrays.
    node.subtreeWidth = node.children.reduce(
      (max, c) => Math.max(max, computeSubtreeWidth(c)),
      NODE_W,
    );
  }
  return node.subtreeWidth;
}

/**
 * Returns the id of the node that an edge should visually originate from when
 * drawing a connection TO `node`. For non-first children of a linear (non-branching)
 * parent this is the last node in the previous sibling's chain — not the shared
 * tree parent — so edges don't pass through intermediate nodes.
 * Ports RouteDiagramLayoutEngine.findLastLayoutNode().
 */
function visualParentId(node) {
  if (!node.parent) return null;
  const parent = node.parent;
  if (BRANCHING_EIPS.has(parent.info.type)) {
    // All branches of a branching EIP connect from the branching node.
    return parent.info.id;
  }
  const idx = parent.children.indexOf(node);
  if (idx === 0) {
    return parent.info.id;
  }
  // Connect from the last node in the previous sibling's subtree chain.
  return lastChainId(parent.children[idx - 1]);
}

/**
 * Traverses the rightmost (last) chain of a subtree and returns its leaf id.
 * Stops at branching EIPs (they have no single continuation point).
 */
function lastChainId(node) {
  if (BRANCHING_EIPS.has(node.info.type) || !node.children.length) {
    return node.info.id;
  }
  return lastChainId(node.children[node.children.length - 1]);
}

/**
 * Walks the tree and populates positions[id] with {x, y, w, h, parentId, type, code, ...}.
 * x, y are the top-left corner of the node box in SVG logical pixels.
 *
 * Returns the bottom-most Y coordinate of the entire subtree rooted at `node`,
 * which the caller uses to position the next sibling without re-traversing the tree.
 *
 * @param {{info, children, parent, subtreeWidth}} node
 * @param {number} x  left edge of the available horizontal band
 * @param {number} y  top of this node
 * @param {number} parentWidth  width of the parent's horizontal band
 * @param {Object} positions   output map: id → position record
 * @returns {number} bottom Y of this subtree (y + h of the deepest node placed)
 */
export function assignPositions(node, x, y, parentWidth, positions) {
  if (!node.info.id) return y + NODE_H;

  const available = Math.max(node.subtreeWidth, parentWidth);
  const nodeX = x + (available - NODE_W) / 2;

  positions[node.info.id] = {
    x: nodeX,
    y,
    w: NODE_W,
    h: NODE_H,
    parentId: visualParentId(node),
    type: node.info.type,
    code: node.info.code,
    description: node.info.description ?? null,
    uri: node.info.uri ?? null,
    statistics: node.info.statistics ?? null,
  };

  if (!node.children.length) return y + NODE_H;

  const childY = y + NODE_H + V_GAP;

  if (BRANCHING_EIPS.has(node.info.type)) {
    let childX = x + (available - node.subtreeWidth) / 2;
    let maxBottom = childY;
    for (const child of node.children) {
      const bottom = assignPositions(child, childX, childY, child.subtreeWidth, positions);
      if (bottom > maxBottom) maxBottom = bottom;
      childX += child.subtreeWidth + H_GAP;
    }
    return maxBottom;
  } else {
    let curY = childY;
    for (const child of node.children) {
      // assignPositions returns the bottom of this child's full subtree — O(n) total.
      curY = assignPositions(child, x, curY, available, positions) + V_GAP;
    }
    return curY - V_GAP;
  }
}

/**
 * Main entry: convert one route object from the route-structure JSON to positioned nodes.
 *
 * @param {{routeId:string, code:Array}} route
 * @returns {{positions:Object, width:number, height:number}}
 */
export function layoutRoute(route) {
  const nodes = route.code ?? [];
  if (!nodes.length) {
    return { positions: {}, width: NODE_W + PADDING * 2, height: NODE_H + PADDING * 2 };
  }

  const tree = buildTree(nodes);
  computeSubtreeWidth(tree);

  const positions = {};
  assignPositions(tree, PADDING, PADDING, tree.subtreeWidth, positions);

  let maxX = 0;
  let maxYVal = 0;
  for (const p of Object.values(positions)) {
    maxX = Math.max(maxX, p.x + p.w);
    maxYVal = Math.max(maxYVal, p.y + p.h);
  }

  return { positions, width: maxX + PADDING, height: maxYVal + PADDING };
}
