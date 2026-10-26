/* Meisterstück: verbindet die Oberfläche mit der lokalen TaHoma-Schnittstelle (Developer Mode).
   Die Oberfläche ruft /api/... auf. Diese Datei übersetzt die Aufrufe in Anfragen an die TaHoma
   und liefert die Antworten im gleichen Format zurück. */
(function () {
  "use strict";
  var native = window.TaHomaNative;
  if (!native) { return; }

  var pending = {};
  var seq = 0;

  window.__nativeResult = function (id, status, text) {
    var cb = pending[id];
    if (!cb) { return; }
    delete pending[id];
    cb(status, text);
  };

  function sleep(ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  }

  // Eine Anfrage an die TaHoma. Gibt immer {status, data} zurück; status 0 heißt "nicht erreichbar".
  function call(method, path, body) {
    return new Promise(function (resolve) {
      var id = "c" + (++seq);
      var timer = setTimeout(function () {
        if (pending[id]) {
          delete pending[id];
          resolve({ status: 0, data: { error: "Zeitüberschreitung" } });
        }
      }, 25000);
      pending[id] = function (status, text) {
        clearTimeout(timer);
        var data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
        resolve({ status: status, data: data });
      };
      native.request(id, method, path, body == null ? "" : JSON.stringify(body));
    });
  }

  function errorText(r) {
    if (r.status === 0) {
      return "TaHoma nicht erreichbar" + (r.data && r.data.error ? " (" + r.data.error + ")" : "");
    }
    if (r.status === 401 || r.status === 403) { return "Der Token wurde abgelehnt"; }
    var d = r.data;
    if (d && (d.error || d.message)) { return String(d.error || d.message); }
    return "Fehler " + r.status;
  }

  function json(obj, status) {
    return new Response(JSON.stringify(obj), {
      status: status || 200,
      headers: { "Content-Type": "application/json" }
    });
  }

  function readConfig() {
    try { return JSON.parse(native.getConfig() || "{}"); } catch (e) { return {}; }
  }

  function mapStates(list) {
    return (list || []).map(function (s) { return { name: s.name, value: s.value }; });
  }

  function mapDevice(d) {
    var def = d.definition || {};
    return {
      device_url: d.deviceURL,
      label: d.label || d.deviceURL,
      widget: def.widgetName || "",
      ui_class: def.uiClass || "",
      states: mapStates(d.states),
      commands: (def.commands || []).map(function (c) { return c.commandName; })
    };
  }

  var realFetch = window.fetch;

  window.fetch = async function (path, opts) {
    if (typeof path !== "string" || path.indexOf("/api/") !== 0) {
      return realFetch.apply(window, arguments);
    }
    opts = opts || {};
    var body = {};
    try { body = opts.body ? JSON.parse(opts.body) : {}; } catch (e) { body = {}; }
    var r;

    if (path === "/api/status") {
      var cfg = readConfig();
      return json({ logged_in: !!(cfg.host && cfg.hasToken) });
    }

    if (path === "/api/login") {
      native.setConfig(String(body.username || ""), String(body.password || ""));
      // 1) Erreichbar? Hier wird auch das Zertifikat der TaHoma gemerkt.
      r = await call("GET", "apiVersion");
      if (r.status !== 200) {
        native.clearConfig();
        return json({ detail: errorText(r) }, 401);
      }
      // 2) Token gültig?
      r = await call("GET", "setup/gateways");
      if (r.status !== 200) {
        native.clearConfig();
        return json({ detail: errorText(r) }, 401);
      }
      return json({ ok: true });
    }

    if (path === "/api/logout") {
      native.clearConfig();
      return json({ ok: true });
    }

    if (path === "/api/devices") {
      r = await call("GET", "setup/devices");
      if (r.status !== 200 || !Array.isArray(r.data)) {
        return json({ detail: errorText(r) }, r.status === 401 ? 401 : 502);
      }
      return json(r.data.map(mapDevice));
    }

    if (path === "/api/scenarios") {
      // Die lokale Schnittstelle kennt keine TaHoma-Szenarien.
      return json([]);
    }

    if (path === "/api/command") {
      r = await call("POST", "exec/apply", {
        label: body.label || "Meisterstück",
        actions: [{
          deviceURL: body.device_url,
          commands: [{ name: body.command, parameters: body.parameters || [] }]
        }]
      });
      if (r.status !== 200) {
        return json({ detail: "Befehl fehlgeschlagen: " + errorText(r) }, 502);
      }
      return json({ exec_id: r.data && r.data.execId });
    }

    if (path.indexOf("/api/scenarios/") === 0) {
      return json({ detail: "Szenarien gibt es in der lokalen Anbindung nicht" }, 502);
    }

    return json({ detail: "Nicht gefunden" }, 404);
  };

  // Ersatz für EventSource: fragt die Ereignisse der TaHoma regelmäßig ab.
  function PollingEvents() {
    this.handlers = {};
    this.onmessage = null;
    this.closed = false;
    this.listener = null;
    this._run();
  }

  PollingEvents.prototype.addEventListener = function (type, fn) {
    (this.handlers[type] = this.handlers[type] || []).push(fn);
  };

  PollingEvents.prototype.close = function () {
    this.closed = true;
    if (this.listener) {
      call("POST", "events/" + this.listener + "/unregister");
      this.listener = null;
    }
  };

  PollingEvents.prototype._emit = function (type, data) {
    var ev = { data: JSON.stringify(data) };
    if (type === "message") {
      if (this.onmessage) { this.onmessage(ev); }
    } else {
      (this.handlers[type] || []).forEach(function (fn) { fn(ev); });
    }
  };

  PollingEvents.prototype._run = async function () {
    var r;
    while (!this.closed) {
      if (!this.listener) {
        r = await call("POST", "events/register");
        if (this.closed) { return; }
        if (r.status === 200 && r.data && r.data.id) {
          this.listener = r.data.id;
          this._emit("ready", {});
        } else {
          this._emit("error", errorText(r));
          await sleep(5000);
          continue;
        }
      }
      r = await call("POST", "events/" + this.listener + "/fetch");
      if (this.closed) { return; }
      if (r.status === 200) {
        var events = Array.isArray(r.data) ? r.data : [];
        for (var i = 0; i < events.length; i++) {
          var e = events[i];
          this._emit("message", {
            name: e.name || "",
            device_url: e.deviceURL || null,
            exec_id: e.execId || null,
            new_state: e.newState || "",
            device_states: mapStates(e.deviceStates)
          });
        }
      } else {
        this._emit("error", errorText(r));
        this.listener = null;
        await sleep(5000);
        continue;
      }
      await sleep(2000);
    }
  };

  window.EventSource = PollingEvents;
})();
