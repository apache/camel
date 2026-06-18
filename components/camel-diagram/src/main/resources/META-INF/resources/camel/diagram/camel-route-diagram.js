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

// ─── Layout engine (ported from RouteDiagramLayoutEngine.java) ───────────────

const NODE_W = 180;
const NODE_H = 36;
const H_GAP = NODE_W / 2;
const V_GAP = 40;
const PADDING = 30;
const ARROW_SIZE = 6;

const BRANCHING_EIPS = new Set([
    'choice', 'multicast', 'doTry', 'loadBalance', 'recipientList', 'circuitBreaker',
]);

function buildTree(nodes) {
    if (!nodes.length) return null;
    const root = { info: nodes[0], children: [], parent: null, subtreeWidth: 0 };
    let current = root;

    for (let i = 1; i < nodes.length; i++) {
        const ni = nodes[i];
        if (!ni.id) {
            console.warn('camel-route-diagram: node without an id is omitted from the diagram', ni);
            continue;
        }
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

function computeSubtreeWidth(node) {
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
        node.subtreeWidth = node.children.reduce(
            (max, c) => Math.max(max, computeSubtreeWidth(c)),
            NODE_W,
        );
    }
    return node.subtreeWidth;
}

function visualParentId(node) {
    if (!node.parent) return null;
    const parent = node.parent;
    if (BRANCHING_EIPS.has(parent.info.type)) {
        return parent.info.id;
    }
    const idx = parent.children.indexOf(node);
    if (idx === 0) {
        return parent.info.id;
    }
    return lastChainId(parent.children[idx - 1]);
}

function lastChainId(node) {
    if (BRANCHING_EIPS.has(node.info.type) || !node.children.length) {
        return node.info.id;
    }
    return lastChainId(node.children[node.children.length - 1]);
}

function assignPositions(node, x, y, parentWidth, positions) {
    if (!node.info.id) {
        console.warn('camel-route-diagram: node without an id is omitted from the diagram', node.info);
        return y + NODE_H;
    }

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
            curY = assignPositions(child, x, curY, available, positions) + V_GAP;
        }
        return curY - V_GAP;
    }
}

function layoutRoute(route) {
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

// ─── Web component ────────────────────────────────────────────────────────────

const TYPE_COLORS = {
    route:          'var(--crd-color-route,          #6366f1)',
    from:           'var(--crd-color-from,           #0ea5e9)',
    to:             'var(--crd-color-to,             #0ea5e9)',
    log:            'var(--crd-color-log,            #64748b)',
    choice:         'var(--crd-color-choice,         #f59e0b)',
    when:           'var(--crd-color-when,           #fbbf24)',
    otherwise:      'var(--crd-color-otherwise,      #fbbf24)',
    doTry:          'var(--crd-color-doTry,          #f59e0b)',
    doCatch:        'var(--crd-color-doCatch,        #fbbf24)',
    doFinally:      'var(--crd-color-doFinally,      #fbbf24)',
    multicast:      'var(--crd-color-multicast,      #8b5cf6)',
    circuitBreaker: 'var(--crd-color-circuitBreaker, #ef4444)',
};

// SVG icon paths from Lucide (https://lucide.dev) — ISC License
// Copyright (c) Lucide Contributors 2022; portions © Cole Bemis 2013-2022 (Feather, MIT)
const ICONS = {
    workflow:            '<rect width="8" height="8" x="3" y="3" rx="2"/><path d="M7 11v4a2 2 0 0 0 2 2h4"/><rect width="8" height="8" x="13" y="13" rx="2"/>',
    'log-in':            '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" x2="3" y1="12" y2="12"/>',
    'log-out':           '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" x2="9" y1="12" y2="12"/>',
    'file-text':         '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v6h6"/><path d="M16 13H8"/><path d="M16 17H8"/><path d="M10 9H8"/>',
    'git-branch':        '<line x1="6" x2="6" y1="3" y2="15"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/>',
    'corner-down-right': '<polyline points="15 10 20 15 15 20"/><path d="M4 4v7a4 4 0 0 0 4 4h12"/>',
    split:               '<path d="M16 3h5v5"/><path d="M8 3H3v5"/><path d="M12 22v-8.3a4 4 0 0 0-1.172-2.872L3 3"/><path d="m15 9 6-6"/>',
    shield:              '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/>',
    'alert-triangle':    '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
    flag:                '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" x2="4" y1="22" y2="15"/>',
    zap:                 '<path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"/>',
    box:                 '<path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/>',
};

const TYPE_ICON = {
    route: 'workflow', from: 'log-in', to: 'log-out', log: 'file-text',
    choice: 'git-branch', when: 'corner-down-right', otherwise: 'corner-down-right',
    doTry: 'shield', doCatch: 'alert-triangle', doFinally: 'flag',
    multicast: 'split', circuitBreaker: 'zap',
};

function iconFor(type) {
    return ICONS[TYPE_ICON[type]] ?? ICONS.box;
}

function nodeColor(type) {
    return TYPE_COLORS[type] ?? 'var(--crd-color-default, #6366f1)';
}

function truncate(text, maxLen = 28) {
    if (!text) return '';
    const clean = text.replace(/^\.+/, '');
    return clean.length > maxLen ? clean.slice(0, maxLen - 1) + '…' : clean;
}

function formatStat(stats) {
    if (!stats) return null;
    const total = stats.exchangesTotal ?? 0;
    const failed = stats.exchangesFailed ?? 0;
    return `✓${total} ✗${failed}`;
}

function esc(s) {
    return String(s ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

// Node ids are interpolated into a DOM id and a url(#...) reference, so restrict them to characters that are
// safe in both contexts (esc() alone would not produce a valid id fragment).
function safeId(id) {
    return String(id).replace(/[^A-Za-z0-9_-]/g, '_');
}

const COMPONENT_STYLE = `
  :host {
    display: block;
    /*
     * fit-content makes the host expand to the SVG's intrinsic width so the
     * parent scroll container sees real overflow and shows a scrollbar.
     * min-width: 100% prevents collapsing when the diagram is narrower than
     * the container.
     */
    width: fit-content;
    min-width: 100%;
    font-family: var(--crd-font, system-ui, sans-serif);
    font-size: var(--crd-font-size, 12px);
    color: var(--crd-fg, #1e293b);
  }
  @media (prefers-color-scheme: dark) {
    :host { color: var(--crd-fg, #e2e8f0); }
  }
  /* Background on .wrap (not :host) so it tracks the SVG width on scroll. */
  .wrap {
    display: flex;
    flex-direction: row;
    align-items: flex-start;
    gap: 24px;
    background: var(--crd-bg, transparent);
    --crd-node-bg: var(--crd-bg, #ffffff);
  }
  @media (prefers-color-scheme: dark) {
    .wrap {
      background: var(--crd-bg, #0f172a);
      --crd-node-bg: var(--crd-bg, #0f172a);
    }
  }
  .route-col { flex-shrink: 0; }
  .error   { color: #ef4444; padding: 8px; }
  .loading { opacity: .6; padding: 8px; }
  .route-label {
    font-weight: 600;
    font-size: 0.9em;
    padding: 4px 0 2px 0;
    opacity: .8;
  }
  svg { display: block; overflow: visible; }
`;

/**
 * A web component that renders Apache Camel route diagrams as interactive SVG.
 *
 * Attributes:
 *   src     - URL of the route-structure dev console endpoint (required)
 *   refresh - polling interval in ms; 0 = disabled (default: 0)
 *   filter  - route ID filter, forwarded as ?filter= query param (default: all routes)
 *
 * CSS custom properties (all optional):
 *   --crd-bg, --crd-node-bg, --crd-fg, --crd-edge, --crd-font, --crd-font-size, --crd-stat
 *   --crd-color-{route,from,to,log,choice,when,otherwise,doTry,doCatch,doFinally,...,default}
 *
 * @since 4.21
 */
class CamelRouteDiagram extends HTMLElement {
    static observedAttributes = ['src', 'refresh', 'filter'];

    #src = '';
    #refresh = 0;
    #filter = '';
    #timer = null;
    #uid = Math.random().toString(36).slice(2);
    #controller = null;
    #data = null;
    #error = null;

    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
    }

    //noinspection JSUnusedGlobalSymbols
    connectedCallback() {
        this.#scheduleRefresh();
        this.#render();
        this.#doFetch();
    }

    //noinspection JSUnusedGlobalSymbols
    disconnectedCallback() {
        clearInterval(this.#timer);
        this.#timer = null;
        this.#controller?.abort();
    }

    //noinspection JSUnusedGlobalSymbols
    attributeChangedCallback(name, oldValue, newValue) {
        if (oldValue === newValue) return;
        switch (name) {
            case 'src':
                this.#src = newValue ?? '';
                if (this.isConnected) this.#doFetch();
                break;
            case 'filter':
                this.#filter = newValue ?? '';
                if (this.isConnected) this.#doFetch();
                break;
            case 'refresh':
                this.#refresh = Number(newValue) || 0;
                if (this.isConnected) this.#scheduleRefresh();
                break;
        }
    }

    #scheduleRefresh() {
        clearInterval(this.#timer);
        this.#timer = null;
        if (this.#refresh > 0) {
            this.#timer = setInterval(() => this.#doFetch(), this.#refresh);
        }
    }

    async #doFetch() {
        const src = this.#src?.trim();
        if (!src) return;
        // Cancel any in-flight request so the last-sent response always wins.
        this.#controller?.abort();
        this.#controller = new AbortController();
        try {
            const url = new URL(src, location.href);
            if (this.#filter) url.searchParams.set('filter', this.#filter);
            url.searchParams.set('metric', 'true');
            const res = await fetch(url, { signal: this.#controller.signal });
            if (!res.ok) {
                this.#error = `HTTP ${res.status} ${res.statusText}`;
                this.#render();
                return;
            }
            const data = await res.json();
            if (!Array.isArray(data?.routes)) {
                this.#error = 'Unexpected response: missing routes array';
                this.#render();
                return;
            }
            this.#data = data;
            this.#error = null;
            this.#render();
        } catch (e) {
            if (e.name !== 'AbortError') {
                this.#error = e.message;
                this.#render();
            }
        }
    }

    #render() {
        this.shadowRoot.innerHTML = this.#buildHTML();
    }

    #buildHTML() {
        const style = `<style>${COMPONENT_STYLE}</style>`;
        if (this.#error) return `${style}<div class="wrap"><p class="error">⚠ ${esc(this.#error)}</p></div>`;
        if (!this.#data) return `${style}<div class="wrap"><p class="loading">Loading diagram…</p></div>`;
        return style + `<div class="wrap">${this.#data.routes.map((r, i) => this.#routeHTML(r, i)).join('')}</div>`;
    }

    #routeHTML(route, routeIdx) {
        const { positions, width, height } = layoutRoute(route);
        const ids = Object.keys(positions);
        const pfx  = `t${this.#uid}r${routeIdx}`;
        const defs = ids.map(id => {
            const p = positions[id];
            return `<clipPath id="${pfx}${safeId(id)}">` +
                   `<rect x="${p.x + 28}" y="${p.y}" width="${NODE_W - 30}" height="${NODE_H}"/></clipPath>`;
        }).join('');
        return `<div class="route-col">
      <div class="route-label">${esc(route.routeId)}</div>
      <svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"
           aria-label="Route diagram for ${esc(route.routeId)}">
        <defs>${defs}</defs>
        ${ids.map(id => this.#edgeHTML(id, positions)).join('')}
        ${ids.map(id => this.#nodeHTML(positions[id], `${pfx}${safeId(id)}`)).join('')}
      </svg>
    </div>`;
    }

    #edgeHTML(id, positions) {
        const pos = positions[id];
        if (!pos.parentId) return '';
        const parent = positions[pos.parentId];
        if (!parent) return '';

        const x1 = parent.x + NODE_W / 2;
        const y1 = parent.y + NODE_H;
        const x2 = pos.x + NODE_W / 2;
        const y2 = pos.y;
        const endY = y2 - ARROW_SIZE / 2;
        const edge = x1 === x2
            ? `M${x1},${y1} L${x2},${endY}`
            : `M${x1},${y1} L${x1},${(y1 + y2) / 2} L${x2},${(y1 + y2) / 2} L${x2},${endY}`;

        return `
      <path
        d="${edge}"
        fill="none"
        stroke="var(--crd-edge, #94a3b8)"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"/>
      <polygon
        points="${x2 - ARROW_SIZE},${y2 - ARROW_SIZE} ${x2},${y2} ${x2 + ARROW_SIZE},${y2 - ARROW_SIZE}"
        fill="var(--crd-edge, #94a3b8)"/>`;
    }

    #nodeHTML(pos, clipId) {
        const label  = truncate(pos.description ?? pos.code);
        const stat   = formatStat(pos.statistics);
        const fill   = nodeColor(pos.type);
        const textX  = pos.x + 30;
        const textY  = pos.y + NODE_H / 2 + 4;

        return `
      <g role="img" aria-label="${esc(pos.type)}: ${esc(label)}">
        <rect x="${pos.x}" y="${pos.y}" width="${NODE_W}" height="${NODE_H}"
              rx="6" ry="6" fill="var(--crd-node-bg, #ffffff)"/>
        <rect x="${pos.x}" y="${pos.y}" width="${NODE_W}" height="${NODE_H}"
              rx="6" ry="6"
              fill="${fill}" fill-opacity="0.15"
              stroke="${fill}" stroke-width="1.5"/>
        <text x="${textX}" y="${stat ? textY - 4 : textY}"
              text-anchor="start" fill="currentColor" font-size="11"
              clip-path="url(#${clipId})">
          ${esc(label)}
        </text>
        ${stat ? `
        <text x="${textX}" y="${pos.y + NODE_H - 3}"
              text-anchor="start" fill="var(--crd-stat, #64748b)" font-size="9"
              clip-path="url(#${clipId})">
          ${esc(stat)}
        </text>` : ''}
        <g transform="translate(${pos.x + 12},${pos.y + (NODE_H - 14) / 2}) scale(0.5833)"
              fill="none" stroke="${fill}" stroke-width="2.4"
              stroke-linecap="round" stroke-linejoin="round" pointer-events="none">
          ${iconFor(pos.type)}
        </g>
      </g>`;
    }
}

customElements.define('camel-route-diagram', CamelRouteDiagram);
