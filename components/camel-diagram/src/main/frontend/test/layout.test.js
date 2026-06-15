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
import { describe, it, expect } from 'vitest';
import { buildTree, computeSubtreeWidth, assignPositions, NODE_W, H_GAP } from '../src/layout.js';

const node = (type, id, level, code = type) => ({ type, id, level, code });

describe('buildTree', () => {
  it('single root node returns tree with no children', () => {
    const tree = buildTree([node('route', 'r1', 0)]);
    expect(tree.info.id).toBe('r1');
    expect(tree.children).toHaveLength(0);
  });

  it('flat siblings share the same parent', () => {
    const nodes = [
      node('route', 'r1', 0),
      node('from',  'f1', 1),
      node('log',   'l1', 1),
      node('to',    't1', 1),
    ];
    const tree = buildTree(nodes);
    expect(tree.children).toHaveLength(3);
    expect(tree.children.map(c => c.info.id)).toEqual(['f1', 'l1', 't1']);
  });

  it('nested choice/when structure builds correct tree', () => {
    const nodes = [
      node('route',     'r1', 0),
      node('from',      'f1', 1),
      node('choice',    'ch', 1),
      node('when',      'wh', 2),
      node('to',        't1', 3),
      node('otherwise', 'ow', 2),
      node('to',        't2', 3),
    ];
    const tree = buildTree(nodes);
    const choice = tree.children.find(c => c.info.type === 'choice');
    expect(choice).toBeDefined();
    expect(choice.children).toHaveLength(2);
    expect(choice.children[0].info.type).toBe('when');
    expect(choice.children[0].children[0].info.type).toBe('to');
    expect(choice.children[1].info.type).toBe('otherwise');
  });
});

describe('computeSubtreeWidth', () => {
  it('leaf node width equals NODE_W', () => {
    const tree = buildTree([node('log', 'l1', 0)]);
    computeSubtreeWidth(tree);
    expect(tree.subtreeWidth).toBe(NODE_W);
  });

  it('branching EIP width is sum of branch widths plus gaps', () => {
    const nodes = [
      node('choice',    'ch', 0),
      node('when',      'w1', 1),
      node('otherwise', 'ow', 1),
    ];
    const tree = buildTree(nodes);
    computeSubtreeWidth(tree);
    expect(tree.subtreeWidth).toBe(NODE_W * 2 + H_GAP);
  });
});

describe('assignPositions', () => {
  it('single-chain route assigns increasing y values', () => {
    const nodes = [
      node('route', 'r1', 0),
      node('from',  'f1', 1),
      node('log',   'l1', 1),
      node('to',    't1', 1),
    ];
    const tree = buildTree(nodes);
    computeSubtreeWidth(tree);
    const positions = {};
    assignPositions(tree, 0, 0, tree.subtreeWidth, positions);

    const ys = ['r1', 'f1', 'l1', 't1'].map(id => positions[id].y);
    expect(ys[1]).toBeGreaterThan(ys[0]);
    expect(ys[2]).toBeGreaterThan(ys[1]);
    expect(ys[3]).toBeGreaterThan(ys[2]);
  });

  it('branching EIP children are laid out side-by-side (different x, same y)', () => {
    const nodes = [
      node('choice',    'ch', 0),
      node('when',      'w1', 1),
      node('otherwise', 'ow', 1),
    ];
    const tree = buildTree(nodes);
    computeSubtreeWidth(tree);
    const positions = {};
    assignPositions(tree, 0, 0, tree.subtreeWidth, positions);

    expect(Math.abs(positions['w1'].x - positions['ow'].x)).toBeGreaterThan(0);
    expect(positions['w1'].y).toBe(positions['ow'].y);
  });
});
