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

// ─── Layout engine (ported from TopologyLayoutEngine.java) ──────────────────

const NODE_W = 180;
const NODE_H = 40;
const V_GAP = 50;
const H_GAP = 30;
const BAND_GAP = 80;
const PADDING = 30;
const ARROW_SIZE = 6;

// ─── External endpoint helpers (ported from TopologyHelper.java) ────────────

function addExternalEndpoints(nodes, edges, data) {
    const ext = data.externalEndpoints;
    if (!ext) return;
    for (const ep of ext) {
        const colonIdx = ep.uri.indexOf(':');
        const desc = colonIdx > 0 ? ep.uri.substring(colonIdx + 1) : ep.uri;
        nodes.push({
            routeId: ep.id,
            description: desc,
            from: ep.uri,
            fromScheme: ep.scheme,
            nodeType: ep.direction === 'in' ? 'external-in' : 'external-out',
            exchangesTotal: ep.exchangesTotal ?? 0,
            exchangesFailed: ep.exchangesFailed ?? 0,
        });
        const edge = { endpoint: ep.uri, connectionType: 'external' };
        if (ep.direction === 'in') {
            edge.fromRouteId = ep.id;
            edge.toRouteId = ep.routeId;
        } else {
            edge.fromRouteId = ep.routeId;
            edge.toRouteId = ep.id;
        }
        edges.push(edge);
    }
}

function expandExternalEdges(nodes, edges) {
    const routeIds = new Set(
        nodes.filter(n => !n.nodeType || !n.nodeType.startsWith('external')).map(n => n.routeId),
    );
    const byEndpoint = new Map();
    for (const e of edges) {
        if (e.connectionType === 'external' && routeIds.has(e.fromRouteId) && routeIds.has(e.toRouteId)) {
            if (!byEndpoint.has(e.endpoint)) byEndpoint.set(e.endpoint, []);
            byEndpoint.get(e.endpoint).push(e);
        }
    }
    if (byEndpoint.size === 0) return;

    let idx = 0;
    for (const [uri, group] of byEndpoint) {
        const colonIdx = uri.indexOf(':');
        const extNode = {
            routeId: 'ext-' + idx++,
            from: uri,
            nodeType: 'external',
            description: colonIdx > 0 ? uri.substring(colonIdx + 1) : uri,
            fromScheme: colonIdx > 0 ? uri.substring(0, colonIdx) : undefined,
            exchangesTotal: 0,
            exchangesFailed: 0,
        };
        nodes.push(extNode);
        for (const orig of group) {
            const origIdx = edges.indexOf(orig);
            if (origIdx >= 0) edges.splice(origIdx, 1);
            edges.push({ fromRouteId: orig.fromRouteId, toRouteId: extNode.routeId, endpoint: uri, connectionType: 'external' });
            edges.push({ fromRouteId: extNode.routeId, toRouteId: orig.toRouteId, endpoint: uri, connectionType: 'external' });
        }
    }
}

// ─── Sugiyama layout ────────────────────────────────────────────────────────

function assignRouteLayers(routeNodes, successors, predecessors) {
    const layers = new Map();
    const routeIds = new Set(routeNodes.map(n => n.routeId));
    const assigned = new Set();

    for (const n of routeNodes) {
        const preds = predecessors.get(n.routeId) ?? [];
        const hasRoutePred = preds.some(p => routeIds.has(p));
        if (n.nodeType === 'trigger' || !hasRoutePred) {
            layers.set(n.routeId, 0);
            assigned.add(n.routeId);
        }
    }
    if (assigned.size === 0 && routeNodes.length > 0) {
        layers.set(routeNodes[0].routeId, 0);
        assigned.add(routeNodes[0].routeId);
    }

    let changed = true;
    while (changed) {
        changed = false;
        for (const n of routeNodes) {
            if (!assigned.has(n.routeId)) continue;
            for (const succ of (successors.get(n.routeId) ?? [])) {
                if (succ === n.routeId || !routeIds.has(succ)) continue;
                const newLayer = layers.get(n.routeId) + 1;
                if (!assigned.has(succ) || layers.get(succ) < newLayer) {
                    layers.set(succ, newLayer);
                    assigned.add(succ);
                    changed = true;
                }
            }
        }
    }

    for (const n of routeNodes) {
        if (!layers.has(n.routeId)) layers.set(n.routeId, 0);
    }
    return layers;
}

function minimizeCrossings(layerGroups, successors, predecessors) {
    function orderByBarycenter(layer, refLayer, neighbors) {
        const refPos = new Map();
        refLayer.forEach((id, i) => refPos.set(id, i));
        const bary = new Map();
        for (const nodeId of layer) {
            const connected = neighbors.get(nodeId) ?? [];
            if (connected.length === 0) { bary.set(nodeId, Infinity); continue; }
            let sum = 0, count = 0;
            for (const nb of connected) {
                const pos = refPos.get(nb);
                if (pos !== undefined) { sum += pos; count++; }
            }
            bary.set(nodeId, count > 0 ? sum / count : Infinity);
        }
        layer.sort((a, b) => bary.get(a) - bary.get(b));
    }

    for (let pass = 0; pass < 4; pass++) {
        for (let i = 1; i < layerGroups.length; i++) {
            orderByBarycenter(layerGroups[i], layerGroups[i - 1], predecessors);
        }
        for (let i = layerGroups.length - 2; i >= 0; i--) {
            orderByBarycenter(layerGroups[i], layerGroups[i + 1], successors);
        }
    }
}

function layoutTopology(nodes, edges) {
    if (!nodes.length) return { layoutNodes: [], layoutEdges: [], totalWidth: 0, totalHeight: 0 };

    const extInNodes = [], extOutNodes = [], routeNodes = [];
    for (const n of nodes) {
        if (n.nodeType === 'external-in') extInNodes.push(n);
        else if (n.nodeType === 'external-out') extOutNodes.push(n);
        else routeNodes.push(n);
    }

    const nodeMap = new Map();
    for (const n of nodes) nodeMap.set(n.routeId, n);

    const successors = new Map(), predecessors = new Map();
    for (const n of nodes) {
        successors.set(n.routeId, []);
        predecessors.set(n.routeId, []);
    }
    for (const e of edges) {
        if (nodeMap.has(e.fromRouteId) && nodeMap.has(e.toRouteId)) {
            successors.get(e.fromRouteId).push(e.toRouteId);
            predecessors.get(e.toRouteId).push(e.fromRouteId);
        }
    }

    const hasExtIn = extInNodes.length > 0;
    const hasExtOut = extOutNodes.length > 0;
    const layers = assignRouteLayers(routeNodes, successors, predecessors);

    if (hasExtIn) {
        for (const [key, val] of layers) layers.set(key, val + 1);
    }
    for (const n of extInNodes) layers.set(n.routeId, 0);

    let maxRouteLayer = 0;
    for (const [key, val] of layers) {
        if (!extOutNodes.some(n => n.routeId === key)) maxRouteLayer = Math.max(maxRouteLayer, val);
    }
    const outLayer = maxRouteLayer + 1;
    for (const n of extOutNodes) layers.set(n.routeId, outLayer);

    const maxLayer = Math.max(...layers.values(), 0);
    const layerGroups = Array.from({ length: maxLayer + 1 }, () => []);
    for (const n of nodes) {
        const l = layers.get(n.routeId) ?? 0;
        layerGroups[l].push(n.routeId);
    }

    minimizeCrossings(layerGroups, successors, predecessors);

    const extInLayer = hasExtIn ? 0 : -1;
    const extOutLayer = hasExtOut ? outLayer : -1;

    // Assign coordinates
    let maxLayerWidth = 0;
    for (const layer of layerGroups) {
        const w = layer.length * (NODE_W + H_GAP) - H_GAP;
        maxLayerWidth = Math.max(maxLayerWidth, w);
    }

    const layoutNodes = new Map();
    let cumY = PADDING;
    for (let li = 0; li < layerGroups.length; li++) {
        const layer = layerGroups[li];
        const lw = layer.length * (NODE_W + H_GAP) - H_GAP;
        const startX = PADDING + (maxLayerWidth - lw) / 2;
        for (let i = 0; i < layer.length; i++) {
            const rid = layer[i];
            const info = nodeMap.get(rid);
            layoutNodes.set(rid, {
                routeId: rid,
                description: info.description,
                from: info.from,
                fromScheme: info.fromScheme,
                nodeType: info.nodeType,
                connectionType: info.connectionType,
                x: startX + i * (NODE_W + H_GAP),
                y: cumY,
                width: NODE_W,
                height: NODE_H,
                layer: li,
                exchangesTotal: info.exchangesTotal ?? 0,
                exchangesFailed: info.exchangesFailed ?? 0,
            });
        }
        let gap = V_GAP;
        if (li === extInLayer || (extOutLayer >= 0 && li === extOutLayer - 1)) gap = BAND_GAP;
        cumY += NODE_H + gap;
    }

    // Build layout edges
    const layoutEdges = [];
    for (const e of edges) {
        const from = layoutNodes.get(e.fromRouteId);
        const to = layoutNodes.get(e.toRouteId);
        if (from && to) {
            const backEdge = (layers.get(e.fromRouteId) ?? 0) >= (layers.get(e.toRouteId) ?? 0)
                && e.fromRouteId !== e.toRouteId;
            const selfLoop = e.fromRouteId === e.toRouteId;
            layoutEdges.push({ from, to, endpoint: e.endpoint, connectionType: e.connectionType, backEdge, selfLoop });
        }
    }

    const allNodes = [...layoutNodes.values()];
    const totalWidth = allNodes.reduce((m, n) => Math.max(m, n.x + NODE_W), 0) + PADDING;
    const totalHeight = allNodes.reduce((m, n) => Math.max(m, n.y + NODE_H), 0) + PADDING;
    return { layoutNodes: allNodes, layoutEdges, totalWidth, totalHeight };
}

// ─── Error handling (ported from TopologyErrorFrame.java) ───────────────────

const LINE_H = 16;

/** The routes reached only on error: a target of an error path that no route calls. */
function errorOnlyRoutes(calls, errors) {
    const called = new Set(calls.map(e => e.toRouteId));
    const answer = new Set();
    for (const e of errors) {
        if (e.toRouteId && !called.has(e.toRouteId)) answer.add(e.toRouteId);
    }
    return answer;
}

/** The external systems only the routes in the frame link to, each under the first route that uses it. */
function frameExternals(nodes, calls, errorOnly) {
    const linked = new Set();
    for (const e of calls) {
        if (!errorOnly.has(e.fromRouteId) && !errorOnly.has(e.toRouteId)) {
            linked.add(e.fromRouteId);
            linked.add(e.toRouteId);
        }
    }
    const byId = new Map(nodes.map(n => [n.routeId, n]));
    const byRoute = new Map();
    const placed = new Set();
    const links = [];
    for (const e of calls) {
        const target = byId.get(e.toRouteId);
        if (errorOnly.has(e.fromRouteId) && target && isExternal(target.nodeType) && !linked.has(e.toRouteId)) {
            if (!placed.has(target.routeId)) {
                placed.add(target.routeId);
                if (!byRoute.has(e.fromRouteId)) byRoute.set(e.fromRouteId, []);
                byRoute.get(e.fromRouteId).push(target);
            }
            links.push(e);
        }
    }
    return { byRoute, links };
}

/**
 * Takes the routes reached only on error, and the external systems only they use, out of the topology: they are
 * removed from the nodes, and their links from the calls.
 */
function takeErrorRoutes(nodes, calls, errors) {
    const errorOnly = errorOnlyRoutes(calls, errors);
    const routes = nodes.filter(n => errorOnly.has(n.routeId));
    const externals = frameExternals(nodes, calls, errorOnly);
    const taken = new Set(errorOnly);
    for (const list of externals.byRoute.values()) {
        for (const n of list) taken.add(n.routeId);
    }
    for (let i = nodes.length - 1; i >= 0; i--) {
        if (taken.has(nodes[i].routeId)) nodes.splice(i, 1);
    }
    for (let i = calls.length - 1; i >= 0; i--) {
        if (taken.has(calls[i].fromRouteId) || taken.has(calls[i].toRouteId)) calls.splice(i, 1);
    }
    return { routes, externals };
}

function errorPathLabel(e) {
    const how = e.via === 'errorHandler' ? 'dead letter' : e.via;
    const what = e.handling == null ? 'decided at runtime'
        : e.handling === 'notHandled' ? 'not handled, goes on to the caller' : e.handling;
    return `${how}, ${what}`;
}

/** The lines under a route of the frame: where its failures come from, one line per route that sends there. */
function frameLines(routeId, errors) {
    const bySource = new Map();
    const seen = new Set();
    for (const e of errors) {
        if (e.toRouteId !== routeId) continue;
        const key = [e.fromRouteId, e.via, e.handling].join('|');
        if (seen.has(key)) continue;
        seen.add(key);
        if (!bySource.has(e.fromRouteId)) bySource.set(e.fromRouteId, []);
        bySource.get(e.fromRouteId).push(errorPathLabel(e));
    }
    const lines = [];
    for (const [from, labels] of bySource) {
        lines.push(from === routeId
            ? `\u26A0 own failures: ${labels.join(' \u00B7 ')}`
            : `\u25C2 ${from}: ${labels.join(' \u00B7 ')}`);
    }
    return lines;
}

function frameNode(info, x, y) {
    return {
        routeId: info.routeId,
        description: info.description,
        from: info.from,
        fromScheme: info.fromScheme,
        nodeType: info.nodeType,
        connectionType: info.connectionType,
        x, y,
        width: NODE_W,
        height: NODE_H,
        layer: -1,
        exchangesTotal: info.exchangesTotal ?? 0,
        exchangesFailed: info.exchangesFailed ?? 0,
    };
}

/**
 * Places the routes reached only on error in a row below the other routes, with their lines under them, and the
 * external systems only they use below that, under their route.
 */
function placeErrorFrame(taken, errors, happyWidth, happyHeight, fontFamily) {
    const top = happyHeight + 12;
    const boxesY = top + 30;
    const step = NODE_W + NODE_W / 2;
    const nodes = [];
    const lines = [];
    const byId = new Map();
    let x = PADDING;
    let maxLines = 0;
    let width = happyWidth;
    for (const info of taken.routes) {
        const n = frameNode(info, x, boxesY);
        nodes.push(n);
        byId.set(info.routeId, n);
        const texts = frameLines(info.routeId, errors);
        maxLines = Math.max(maxLines, texts.length);
        texts.forEach((text, i) => {
            const w = measureWidth(text, 11, fontFamily) ?? text.length * 6.5;
            lines.push({ x, y: boxesY + NODE_H + LINE_H * (i + 1), text, width: w });
            width = Math.max(width, x + w + PADDING);
        });
        width = Math.max(width, x + NODE_W + PADDING);
        x += step;
    }
    let bottom = boxesY + NODE_H + LINE_H * (maxLines + 1);
    // below the lines of their route, the arrow from the route passing behind them
    const externalsY = boxesY + NODE_H + LINE_H * maxLines + 40;
    for (const [routeId, list] of taken.externals.byRoute) {
        const route = byId.get(routeId);
        if (!route) continue;
        let y = externalsY;
        for (const info of list) {
            const n = frameNode(info, route.x, y);
            nodes.push(n);
            byId.set(info.routeId, n);
            y += NODE_H + 30;
            bottom = Math.max(bottom, n.y + NODE_H + 16);
        }
    }
    const edges = [];
    for (const e of taken.externals.links) {
        const from = byId.get(e.fromRouteId);
        const to = byId.get(e.toRouteId);
        if (from && to) {
            edges.push({ from, to, endpoint: e.endpoint, connectionType: e.connectionType, backEdge: false, selfLoop: false });
        }
    }
    return { top, nodes, edges, lines, bottom, width, height: bottom + 12 };
}

// ─── Web component ──────────────────────────────────────────────────────────

function isExternal(nodeType) {
    return nodeType === 'external-in' || nodeType === 'external-out' || nodeType === 'external';
}

// Measure text with an offscreen 2D canvas using the same font the SVG inherits. Returns null when no canvas
// context is available so callers can fall back to a character-count estimate.
let measureCtx = null;
function measureWidth(text, fontSize, fontFamily = 'system-ui, sans-serif') {
    if (measureCtx === null) {
        measureCtx = document.createElement('canvas').getContext('2d') || false;
    }
    if (!measureCtx) return null;
    measureCtx.font = `${fontSize}px ${fontFamily}`;
    return measureCtx.measureText(String(text)).width;
}

// Trim a label so it fits a pixel width. SVG <text> is proportional, so a fixed character budget cannot
// guarantee a label stays inside its node; drop trailing characters until the text plus an ellipsis fits.
// When `paren` is set the surrounding parentheses are part of the measurement so the ellipsis lands inside them.
function fitText(text, maxWidth, fontSize = 11, paren = false, fontFamily = 'system-ui, sans-serif') {
    if (!text) return '';
    const wrap = paren ? (v) => `(${v})` : (v) => v;
    let s = String(text).replace(/^\.+/, '');
    let w = measureWidth(wrap(s), fontSize, fontFamily);
    if (w === null) {
        const maxLen = Math.max(1, Math.floor(maxWidth / (fontSize * 0.6)) - (paren ? 2 : 0));
        return s.length > maxLen ? wrap(s.slice(0, maxLen - 1) + '…') : wrap(s);
    }
    if (w <= maxWidth) return wrap(s);
    while (s.length > 0 && measureWidth(wrap(s + '…'), fontSize, fontFamily) > maxWidth) {
        s = s.slice(0, -1);
    }
    return wrap(s.replace(/\s+$/, '') + '…');
}

function esc(s) {
    return String(s ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

const COMPONENT_STYLE = `
  :host {
    display: block;
    width: fit-content;
    min-width: 100%;
    font-family: var(--ctd-font, system-ui, sans-serif);
    font-size: var(--ctd-font-size, 12px);
    color: var(--ctd-fg, #1e293b);
  }
  @media (prefers-color-scheme: dark) {
    :host { color: var(--ctd-fg, #e2e8f0); }
  }
  .wrap {
    display: flex;
    align-items: flex-start;
    justify-content: center;
    background: var(--ctd-bg, transparent);
    /* Node-box fill tracks the theme so light text never lands on a light box (and vice versa). */
    --ctd-node-bg: var(--ctd-bg, #ffffff);
    padding: 12px;
  }
  @media (prefers-color-scheme: dark) {
    .wrap {
      background: var(--ctd-bg, #0f172a);
      --ctd-node-bg: var(--ctd-bg, #0f172a);
    }
  }
  .error   { color: #ef4444; padding: 8px; }
  .loading { opacity: .6; padding: 8px; }
  svg { display: block; overflow: visible; }
`;

// SVG icon paths from Lucide (https://lucide.dev) — ISC License
// Copyright (c) Lucide Contributors 2022; portions (c) Cole Bemis 2013-2022 (Feather, MIT)
const ICONS = {
    workflow:  '<rect width="8" height="8" x="3" y="3" rx="2"/><path d="M7 11v4a2 2 0 0 0 2 2h4"/><rect width="8" height="8" x="13" y="13" rx="2"/>',
    'log-in':  '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" x2="3" y1="12" y2="12"/>',
    zap:       '<path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"/>',
    cloud:     '<path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/>',
};

function iconFor(nodeType) {
    if (isExternal(nodeType)) return ICONS.cloud;
    if (nodeType === 'trigger') return ICONS.zap;
    return ICONS.workflow;
}

function nodeColor(nodeType) {
    if (isExternal(nodeType)) return 'var(--ctd-color-external, #64748b)';
    if (nodeType === 'trigger') return 'var(--ctd-color-trigger, #f59e0b)';
    return 'var(--ctd-color-route, #6366f1)';
}

/**
 * A web component that renders Apache Camel topology diagrams as interactive SVG.
 *
 * Attributes:
 *   src       - URL of the route-topology dev console endpoint (required)
 *   refresh   - polling interval in ms; 0 = disabled (default: 0)
 *   metric    - show live metrics (default: true)
 *   external  - include external endpoint nodes (default: true)
 *   interlink - show intermediary nodes for routes connected via shared externals (default: true)
 *   errors    - "true" draws the routes reached only on error (such as the target of a dead letter channel) in a
 *               frame below the other routes, with where their failures come from and the external systems only
 *               they use (default: false)
 *
 * CSS custom properties (all optional):
 *   --ctd-bg, --ctd-node-bg, --ctd-fg, --ctd-edge, --ctd-font, --ctd-font-size
 *   --ctd-color-route, --ctd-color-trigger, --ctd-color-external
 *
 * @since 4.21
 */
class CamelTopologyDiagram extends HTMLElement {
    static observedAttributes = ['src', 'refresh', 'metric', 'external', 'interlink', 'errors'];

    #src = '';
    #refresh = 0;
    #metric = true;
    #external = true;
    #interlink = true;
    #errors = false;
    #timer = null;
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
            case 'metric':
                this.#metric = newValue !== 'false';
                if (this.isConnected) this.#doFetch();
                break;
            case 'external':
                this.#external = newValue !== 'false';
                if (this.isConnected) this.#render();
                break;
            case 'interlink':
                this.#interlink = newValue !== 'false';
                if (this.isConnected) this.#render();
                break;
            case 'errors':
                this.#errors = newValue != null && newValue !== 'false';
                if (this.isConnected) this.#render();
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
        this.#controller?.abort();
        this.#controller = new AbortController();
        try {
            const url = new URL(src, location.href);
            url.searchParams.set('external', 'true');
            url.searchParams.set('metric', String(this.#metric));
            const res = await fetch(url, {
                signal: this.#controller.signal,
                headers: { 'Accept': 'application/json' },
            });
            if (!res.ok) {
                this.#error = `HTTP ${res.status} ${res.statusText}`;
                this.#render();
                return;
            }
            let data = await res.json();
            if (data && !Array.isArray(data.nodes) && data['route-topology']) {
                data = data['route-topology'];
            }
            if (!Array.isArray(data?.nodes)) {
                this.#error = 'Unexpected response: missing nodes array';
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
        if (!this.#data) return `${style}<div class="wrap"><p class="loading">Loading topology…</p></div>`;
        return style + `<div class="wrap">${this.#topologyHTML()}</div>`;
    }

    #topologyHTML() {
        const data = this.#data;
        const fontFamily = getComputedStyle(this).getPropertyValue('--ctd-font').trim() || 'system-ui, sans-serif';

        // Parse nodes and edges
        const nodes = (data.nodes ?? []).map(n => ({
            routeId: n.routeId,
            description: n.description ?? null,
            from: n.from,
            fromScheme: n.fromScheme,
            nodeType: n.nodeType ?? 'route',
            exchangesTotal: n.exchangesTotal ?? 0,
            exchangesFailed: n.exchangesFailed ?? 0,
        }));
        // an error path is no call: a route sends there only when it handles a failure (Camel 4.23 or newer)
        const edges = (data.edges ?? []).filter(e => e.kind !== 'error').map(e => ({
            fromRouteId: e.fromRouteId,
            toRouteId: e.toRouteId,
            endpoint: e.endpoint,
            connectionType: e.connectionType ?? 'internal',
        }));
        const errors = (data.edges ?? []).filter(e => e.kind === 'error');

        if (this.#external) {
            addExternalEndpoints(nodes, edges, data);
            if (this.#interlink) {
                expandExternalEdges(nodes, edges);
            }
        }

        // the routes reached only on error go into a frame below the others, with the external systems only they use
        const taken = this.#errors ? takeErrorRoutes(nodes, edges, errors) : null;
        const { layoutNodes, layoutEdges, totalWidth, totalHeight } = layoutTopology(nodes, edges);
        if (!layoutNodes.length && !taken?.routes.length) return '<p class="loading">No routes</p>';

        let edgeSvg = layoutEdges.map(e => this.#edgeHTML(e)).join('');
        let nodeSvg = layoutNodes.map(n => this.#nodeHTML(n, fontFamily)).join('');
        let frameSvg = '';
        let lineSvg = '';
        let width = totalWidth;
        let height = totalHeight;
        if (taken?.routes.length) {
            const frame = placeErrorFrame(taken, errors, totalWidth, totalHeight, fontFamily);
            width = Math.max(width, frame.width);
            height = frame.height;
            const title = 'Error handling';
            const titleW = (measureWidth(title, 11, fontFamily) ?? title.length * 6.5) + 8;
            frameSvg = `
      <rect x="${PADDING / 2}" y="${frame.top}" width="${width - PADDING}" height="${frame.bottom - frame.top}"
            rx="8" ry="8" fill="none" stroke="var(--ctd-edge, #94a3b8)" stroke-width="1.5" stroke-dasharray="6 4"/>
      <rect x="${PADDING}" y="${frame.top - 8}" width="${titleW}" height="16" fill="var(--ctd-node-bg, #ffffff)"/>
      <text x="${PADDING + 4}" y="${frame.top + 4}" font-size="11" font-weight="600" fill="currentColor">${title}</text>`;
            edgeSvg += frame.edges.map(e => this.#edgeHTML(e)).join('');
            nodeSvg += frame.nodes.map(n => this.#nodeHTML(n, fontFamily)).join('');
            lineSvg = frame.lines.map(l => `
      <rect x="${l.x}" y="${l.y - 11}" width="${l.width}" height="${LINE_H - 1}" fill="var(--ctd-node-bg, #ffffff)"/>
      <text x="${l.x}" y="${l.y}" font-size="11" fill="currentColor">${esc(l.text)}</text>`).join('');
        }

        return `<svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"
                     aria-label="Route topology diagram">
            ${frameSvg}${edgeSvg}${nodeSvg}${lineSvg}
        </svg>`;
    }

    #edgeHTML(edge) {
        if (edge.selfLoop) return '';

        const ext = isExternal(edge.from.nodeType) || isExternal(edge.to.nodeType);
        const dashAttr = ext ? ' stroke-dasharray="6 4"' : '';
        const fromCx = edge.from.x + NODE_W / 2;
        const fromBy = edge.from.y + NODE_H;
        const toCx = edge.to.x + NODE_W / 2;
        const toTy = edge.to.y;
        const endY = toTy - ARROW_SIZE / 2;

        const path = fromCx === toCx
            ? `M${fromCx},${fromBy} L${toCx},${endY}`
            : `M${fromCx},${fromBy} L${fromCx},${(fromBy + toTy) / 2} L${toCx},${(fromBy + toTy) / 2} L${toCx},${endY}`;

        return `
      <path d="${path}" fill="none"
            stroke="var(--ctd-edge, #94a3b8)" stroke-width="1.5"
            stroke-linecap="round" stroke-linejoin="round"${dashAttr}/>
      <polygon
        points="${toCx - ARROW_SIZE},${toTy - ARROW_SIZE} ${toCx},${toTy} ${toCx + ARROW_SIZE},${toTy - ARROW_SIZE}"
        fill="var(--ctd-edge, #94a3b8)"/>`;
    }

    #nodeHTML(node, fontFamily) {
        const ext = isExternal(node.nodeType);
        const fill = nodeColor(node.nodeType);
        const dashAttr = ext ? ' stroke-dasharray="4 3"' : '';

        const stat = this.#metric && node.exchangesTotal > 0
            ? { total: node.exchangesTotal, failed: node.exchangesFailed } : null;

        // Labels start 30px in (icon zone); keep an 8px margin before the right border. When a metric badge is
        // shown in the top-right corner, reserve its measured width on the first line so the label cannot run
        // under it. The sub-label sits lower and never collides with the badge, so it uses the full width.
        let labelMax = NODE_W - 38;
        if (stat) {
            const metricText = stat.failed > 0 ? `${stat.total - stat.failed}  ${stat.failed}` : `${stat.total - stat.failed}`;
            const metricWidth = measureWidth(metricText, 9, fontFamily) ?? 36;
            labelMax = NODE_W - 8 - metricWidth - 6 - 30;
        }
        const fullLabel = ext ? node.from : (node.description ?? node.routeId);
        const label = fitText(fullLabel, labelMax, 11, false, fontFamily);
        const subLabel = ext ? null : fitText(node.from, NODE_W - 38, 9, true, fontFamily);

        // Only attach a hover tooltip when text was actually trimmed (the label ends with the ellipsis, or the
        // parenthesised sub-label ends with one). Show the full description and the full from-uri.
        const trimmed = label.endsWith('…') || (!!subLabel && subLabel.endsWith('…)'));
        let titleText = trimmed ? (fullLabel ?? '') : null;
        if (titleText != null && !ext && node.from) {
            titleText += `\n(${node.from})`;
        }
        const title = titleText != null ? `<title>${esc(titleText)}</title>` : '';

        const textX = node.x + 30;
        const baseY = node.y + NODE_H / 2;

        let line1Y, line2Y;
        if (ext || !subLabel) {
            line1Y = baseY + 4;
            line2Y = null;
        } else {
            line1Y = baseY - 2;
            line2Y = baseY + 12;
        }

        return `
      <g role="img" aria-label="${esc(node.routeId)}: ${esc(label)}">
        ${title}
        <rect x="${node.x}" y="${node.y}" width="${NODE_W}" height="${NODE_H}"
              rx="6" ry="6" fill="var(--ctd-node-bg, #ffffff)"/>
        <rect x="${node.x}" y="${node.y}" width="${NODE_W}" height="${NODE_H}"
              rx="6" ry="6"
              fill="${fill}" fill-opacity="${ext ? '0.08' : '0.15'}"
              stroke="${fill}" stroke-width="1.5"${dashAttr}/>
        <text x="${textX}" y="${line1Y}"
              text-anchor="start" fill="currentColor" font-size="11">
          ${esc(label)}
        </text>
        ${line2Y != null ? `
        <text x="${textX}" y="${line2Y}"
              text-anchor="start" fill="currentColor" font-size="9" opacity="0.7">
          ${esc(subLabel)}
        </text>` : ''}
        ${stat ? `
        <text x="${node.x + NODE_W - 8}" y="${node.y + 12}"
              text-anchor="end" font-size="9">
          <tspan fill="#22c55e">${stat.total - stat.failed}</tspan>${stat.failed > 0
            ? `<tspan dx="4" fill="#ef4444">${stat.failed}</tspan>` : ''}
        </text>` : ''}
        <g transform="translate(${node.x + 10},${node.y + (NODE_H - 14) / 2}) scale(0.5833)"
              fill="none" stroke="${fill}" stroke-width="2.4"
              stroke-linecap="round" stroke-linejoin="round" pointer-events="none">
          ${iconFor(node.nodeType)}
        </g>
      </g>`;
    }
}

customElements.define('camel-topology-diagram', CamelTopologyDiagram);
