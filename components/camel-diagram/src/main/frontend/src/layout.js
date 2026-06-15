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
    node.subtreeWidth = Math.max(NODE_W, ...node.children.map(computeSubtreeWidth));
  }
  return node.subtreeWidth;
}

/**
 * Walks the tree and populates positions[id] with {x, y, w, h, parentId, type, code, ...}.
 * x, y are the top-left corner of the node box in SVG logical pixels.
 *
 * @param {{info, children, parent, subtreeWidth}} node
 * @param {number} x  left edge of the available horizontal band
 * @param {number} y  top of this node
 * @param {number} parentWidth  width of the parent's horizontal band
 * @param {Object} positions   output map: id → position record
 */
export function assignPositions(node, x, y, parentWidth, positions) {
  const available = Math.max(node.subtreeWidth, parentWidth);
  const nodeX = x + (available - NODE_W) / 2;

  positions[node.info.id] = {
    x: nodeX,
    y,
    w: NODE_W,
    h: NODE_H,
    parentId: node.parent ? node.parent.info.id : null,
    type: node.info.type,
    code: node.info.code,
    description: node.info.description ?? null,
    uri: node.info.uri ?? null,
    statistics: node.info.statistics ?? null,
  };

  if (!node.children.length) return;

  const childY = y + NODE_H + V_GAP;

  if (BRANCHING_EIPS.has(node.info.type)) {
    let childX = x + (available - node.subtreeWidth) / 2;
    for (const child of node.children) {
      assignPositions(child, childX, childY, child.subtreeWidth, positions);
      childX += child.subtreeWidth + H_GAP;
    }
  } else {
    let curY = childY;
    for (const child of node.children) {
      assignPositions(child, x, curY, available, positions);
      curY = subtreeMaxY(child, positions) + V_GAP;
    }
  }
}

function subtreeMaxY(node, positions) {
  const pos = positions[node.info.id];
  let my = pos ? pos.y + pos.h : 0;
  for (const child of node.children) {
    my = Math.max(my, subtreeMaxY(child, positions));
  }
  return my;
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
