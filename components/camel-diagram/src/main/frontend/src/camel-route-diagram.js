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
import { LitElement, html, svg, css, nothing } from 'lit';
import { layoutRoute, NODE_W, NODE_H } from './layout.js';

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

/**
 * A web component that renders Apache Camel route diagrams as interactive SVG.
 *
 * Attributes:
 *   src     - URL of the route-structure dev console endpoint (required)
 *   refresh - polling interval in ms; 0 = disabled (default: 0)
 *   filter  - route ID filter, forwarded as ?filter= query param (default: all routes)
 *
 * CSS custom properties (all optional):
 *   --crd-bg, --crd-fg, --crd-edge, --crd-font, --crd-font-size, --crd-stat
 *   --crd-color-{route,from,to,log,choice,when,otherwise,doTry,doCatch,doFinally,...,default}
 *
 * @since 4.21
 */
class CamelRouteDiagram extends LitElement {
  static properties = {
    src:    { type: String },
    refresh: { type: Number },
    filter: { type: String },
    _data:  { state: true },
    _error: { state: true },
  };

  static styles = css`
    :host {
      display: block;
      font-family: var(--crd-font, system-ui, sans-serif);
      font-size: var(--crd-font-size, 12px);
      background: var(--crd-bg, transparent);
      color: var(--crd-fg, #1e293b);
    }
    @media (prefers-color-scheme: dark) {
      :host {
        background: var(--crd-bg, #0f172a);
        color: var(--crd-fg, #e2e8f0);
      }
    }
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

  #timer = null;
  #uid = Math.random().toString(36).slice(2);
  #controller = null;

  constructor() {
    super();
    this.src = '';
    this.refresh = 0;
    this.filter = '';
    this._data = null;
    this._error = null;
  }

  connectedCallback() {
    super.connectedCallback();
    // On reconnect (hasUpdated is true) the reactive properties haven't changed,
    // so updated() won't fire automatically — force it with requestUpdate().
    // On first connect, super.connectedCallback() already schedules the update;
    // this requestUpdate() coalesces harmlessly.
    this.requestUpdate();
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    clearInterval(this.#timer);
    this.#timer = null;
    this.#controller?.abort();
  }

  updated(changedProperties) {
    // React to src/filter/refresh changes and to reconnect (changedProperties is
    // empty when requestUpdate() was called with no property change on reconnect).
    const srcOrFilterChanged = changedProperties.has('src') || changedProperties.has('filter');
    const refreshChanged = changedProperties.has('refresh');
    const isReconnect = changedProperties.size === 0;

    if (refreshChanged || isReconnect) {
      clearInterval(this.#timer);
      this.#timer = null;
      if (this.refresh > 0) {
        this.#timer = setInterval(() => this.#doFetch(), this.refresh);
      }
    }
    if (srcOrFilterChanged || isReconnect) {
      this.#doFetch();
    }
  }

  async #doFetch() {
    const src = this.src?.trim();
    if (!src) return;
    // Cancel any in-flight request so the last-sent response always wins.
    this.#controller?.abort();
    this.#controller = new AbortController();
    try {
      const url = new URL(src, location.href);
      if (this.filter) url.searchParams.set('filter', this.filter);
      url.searchParams.set('metric', 'true');
      const res = await fetch(url, { signal: this.#controller.signal });
      if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
      const data = await res.json();
      if (!Array.isArray(data?.routes)) {
        throw new Error('Unexpected response: missing routes array');
      }
      this._data = data;
      this._error = null;
    } catch (e) {
      if (e.name !== 'AbortError') {
        this._error = e.message;
      }
    }
  }

  render() {
    if (this._error) return html`<p class="error">⚠ ${this._error}</p>`;
    if (!this._data) return html`<p class="loading">Loading diagram…</p>`;

    return html`${this._data.routes.map(r => this.#renderRoute(r))}`;
  }

  #renderRoute(route) {
    const { positions, width, height } = layoutRoute(route);
    const ids = Object.keys(positions);
    const markerId = `arrow-${this.#uid}`;
    // The <marker> is defined inside the same <svg> it is used in so that
    // url(#id) paint-server references resolve correctly in all browsers,
    // including Firefox which does not resolve them across sibling <svg> elements.
    return html`
      <div class="route-label">${route.routeId}</div>
      <svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"
           aria-label="Route diagram for ${route.routeId}">
        <defs>
          <marker id="${markerId}" markerWidth="8" markerHeight="8"
                  refX="6" refY="3" orient="auto">
            <path d="M0,0 L0,6 L8,3 z" fill="var(--crd-edge, #94a3b8)"/>
          </marker>
        </defs>
        ${ids.map(id => this.#renderEdge(id, positions, markerId))}
        ${ids.map(id => this.#renderNode(id, positions[id]))}
      </svg>
    `;
  }

  #renderEdge(id, positions, markerId) {
    const pos = positions[id];
    if (!pos.parentId) return nothing;
    const parent = positions[pos.parentId];
    if (!parent) return nothing;

    const x1 = parent.x + NODE_W / 2;
    const y1 = parent.y + NODE_H;
    const x2 = pos.x + NODE_W / 2;
    const y2 = pos.y;
    const my = (y1 + y2) / 2;

    return svg`<path
      d="M${x1},${y1} C${x1},${my} ${x2},${my} ${x2},${y2}"
      fill="none"
      stroke="var(--crd-edge, #94a3b8)"
      stroke-width="1.5"
      marker-end="url(#${markerId})"/>`;
  }

  #renderNode(id, pos) {
    const label = truncate(pos.description ?? pos.code);
    const stat  = formatStat(pos.statistics);
    const fill  = nodeColor(pos.type);
    const cx    = pos.x + NODE_W / 2;
    const textY = pos.y + NODE_H / 2 + 4;

    return svg`
      <g role="img" aria-label="${pos.type}: ${label}">
        <rect x="${pos.x}" y="${pos.y}" width="${NODE_W}" height="${NODE_H}"
              rx="6" ry="6"
              fill="${fill}" fill-opacity="0.15"
              stroke="${fill}" stroke-width="1.5"/>
        <text x="${cx}" y="${textY}"
              text-anchor="middle" dominant-baseline="auto"
              fill="currentColor" font-size="11">
          ${label}
        </text>
        ${stat ? svg`
          <text x="${cx}" y="${pos.y + NODE_H - 3}"
                text-anchor="middle"
                fill="var(--crd-stat, #64748b)" font-size="9">
            ${stat}
          </text>` : nothing}
      </g>`;
  }
}

customElements.define('camel-route-diagram', CamelRouteDiagram);
