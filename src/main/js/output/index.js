var avg, counter, graph, i, newCounter, newGraph, pushWithWindow, runningWindowMs, socket, stop, websocketFsm, wsUri;

wsUri = "ws://localhost:8080/";

runningWindowMs = 1000;

websocketFsm = function() {
  var websocket;
  websocket = new WebSocket(wsUri);
  return new machina.Fsm({
    initialState: "waiting",
    states: {
      waiting: {
        _onEnter: function() {
          websocket.onopen = (function(_this) {
            return function(evt) {
              return _this.handle("open", evt);
            };
          })(this);
          websocket.onclose = (function(_this) {
            return function(evt) {
              return _this.handle("closed", evt);
            };
          })(this);
          websocket.onmessage = (function(_this) {
            return function(evt) {
              return _this.handle("message", evt.data);
            };
          })(this);
          return websocket.onerror = function(evt) {
            return this.handle("error", evt);
          };
        },
        open: function(evt) {
          console.log(evt);
          console.log("connected!");
          return this.transition("open");
        },
        "*": function(payload, a) {
          console.log("deferring", payload, a);
          return this.deferUntilTransition();
        }
      },
      open: {
        "publish": function(_arg) {
          var payload, topic;
          topic = _arg[0], payload = _arg[1];
          return websocket.send(JSON.stringify([topic, payload]));
        },
        "close": function() {
          return websocket.close();
        },
        "closed": function() {
          return this.transition("closd");
        },
        "message": function(data) {
          var channel, payload, _ref;
          _ref = JSON.parse(data), channel = _ref[0], payload = _ref[1];
          return this.emit(channel, payload);
        }
      },
      closed: {
        "close": function() {},
        "*": function() {
          return console.log("we're closed");
        }
      }
    }
  });
};

newGraph = function() {
  var calcColor, doRender, g, nodeHtml, nodeIdToIndexMap, nodeIndex, nodes, registerEdge, registerNode, render, stats, svg, svgGroup, toggleStates, _nodeIndex;
  nodeIdToIndexMap = {};
  _nodeIndex = 0;
  nodeIndex = function(nodeId) {
    var idx;
    if (nodeIdToIndexMap[nodeId] != null) {
      return nodeIdToIndexMap[nodeId];
    } else {
      idx = _nodeIndex;
      _nodeIndex += 1;
      return nodeIdToIndexMap[nodeId] = idx;
    }
  };
  g = new dagreD3.graphlib.Graph().setGraph({}).setDefaultEdgeLabel(function() {
    return {};
  });
  render = new dagreD3.render();
  svg = d3.select("svg");
  svgGroup = svg.select("g");
  doRender = function() {
    var xCenterOffset;
    g.nodes().forEach(function(v) {
      var node;
      node = g.node(v);
      node.rx = node.ry = 5;
    });
    svgGroup.call(render, g);
    xCenterOffset = (svg.attr("width") - g.graph().width) / 2;
    svgGroup.attr("transform", "translate(" + xCenterOffset + ", 20)");
    return svg.attr("height", g.graph().height + 40);
  };
  nodeHtml = function(node) {
    var html;
    html = "<div>";
    html += "<span class=node-name>" + node.name + "</span>";
    html += "<span class=message-count>" + (stats.counts[node.id] || 0) + "</span>";
    html += "<span class=message-rate>" + ((stats.rates[node.id] || 0).toFixed(1)) + " / s</span>";
    html += "</div>";
    return html;
  };
  nodes = {};
  registerNode = function(_arg) {
    var id, name;
    id = _arg.id, name = _arg.name;
    console.log("graph.new-node", id);
    nodes[id] = {
      id: id,
      name: name,
      messageCount: 0,
      deliveries: []
    };
    nodes[id].deliveries.name = name;
    return g.setNode(nodeIndex(id), {
      id: id,
      name: name,
      labelType: "html",
      label: nodeHtml(nodes[id]),
      "class": "type-" + id
    });
  };
  registerEdge = function(_arg) {
    var from, properties, to;
    from = _arg.from, to = _arg.to, properties = _arg.properties;
    console.log("graph.new-edge", from, to, properties);
    return g.setEdge(nodeIndex(from), nodeIndex(to), {
      label: properties.label,
      "class": "jerk",
      style: properties.subStream ? "stroke: #f66; stroke-width: 2px" : null
    });
  };
  toggleStates = {};
  stats = {
    counts: {},
    rates: {},
    maxRate: 1
  };
  calcColor = function(percent) {
    var shadeB, shadeG, shadeR;
    shadeR = Math.round(255 - (percent * 255));
    shadeG = Math.round(255 - (percent * 32));
    shadeB = Math.round(255 - (percent * 255));
    return "RGB(" + shadeR + ", " + shadeG + ", " + shadeB + ")";
  };
  return new machina.Fsm({
    initialState: "init",
    states: {
      init: {
        "graph.new-node": registerNode,
        "graph.new-edge": registerEdge,
        "graph.initialized": function() {
          return this.transition("drawing");
        },
        "*": function(payload, a) {
          return console.log("unknown msg:", payload, a);
        }
      },
      drawing: {
        _onEnter: function() {
          var error;
          try {
            return doRender();
          } catch (_error) {
            error = _error;
            console.log(error);
            return console.log(error.stack);
          }
        },
        "graph.new-node": function(data) {
          registerNode(data);
          return doRender();
        },
        "graph.new-edge": function(data) {
          registerEdge(data);
          return doRender();
        },
        "update-stats": function(_stats) {
          return stats = _stats;
        },
        "render": function() {
          g.nodes().forEach(function(idx) {
            var gNode, node, rate;
            gNode = g.node(idx);
            node = nodes[gNode.id];
            rate = stats.rates[node.id];
            gNode.label = nodeHtml(node);
            return gNode.style = "fill: " + (calcColor(rate / stats.maxRate));
          });
          return svgGroup.call(render, g);
        },
        "*": function(payload, a) {
          return console.log("unknown msg:", payload, a);
        }
      }
    }
  });
};

avg = function(values) {
  return _.reduce(values, (function(a, b) {
    return a + b;
  }), 0) / values.length;
};

pushWithWindow = function(arr, value, windowSize) {
  arr.push(value);
  if (arr.length > windowSize) {
    arr.shift();
  }
};

newCounter = function() {
  var RUNNING_MAX_AVG_WINDOW, RUNNING_RATE_AVG_WINDOW, calcMessageRate, maxes, nodeDeliveries, nodeRates, nodeTotals;
  nodeTotals = {};
  nodeRates = {};
  nodeDeliveries = {};
  RUNNING_RATE_AVG_WINDOW = 5;
  RUNNING_MAX_AVG_WINDOW = 10;
  maxes = [];
  calcMessageRate = function(deliveries) {
    var border;
    border = (new Date) - runningWindowMs;
    while (deliveries[0] && deliveries[0] < border) {
      deliveries.shift();
    }
    if (deliveries.length === 0) {
      return 0;
    }
    return (deliveries.length / runningWindowMs) * 1000;
  };
  return new machina.Fsm({
    initialState: "init",
    states: {
      init: {
        "node.message": function(_arg) {
          var nodeId;
          nodeId = _arg.nodeId;
          nodeTotals[nodeId] = (nodeTotals[nodeId] || 0) + 1;
          return (nodeDeliveries[nodeId] || (nodeDeliveries[nodeId] = [])).push(new Date);
        },
        "calculate": function() {
          var currentRates, deliveries, frameMax, id, maxRateAvg, rate, rates;
          currentRates = {};
          frameMax = 0;
          for (id in nodeDeliveries) {
            deliveries = nodeDeliveries[id];
            rates = (nodeRates[id] || (nodeRates[id] = []));
            pushWithWindow(rates, calcMessageRate(deliveries), RUNNING_RATE_AVG_WINDOW);
            rate = avg(rates);
            if (rate > frameMax) {
              frameMax = rate;
            }
            currentRates[id] = rate;
          }
          pushWithWindow(maxes, frameMax, RUNNING_MAX_AVG_WINDOW);
          maxRateAvg = avg(maxes);
          return this.emit("stats", {
            maxRate: maxRateAvg,
            rates: currentRates,
            counts: nodeTotals
          });
        }
      }
    }
  });
};

graph = newGraph();

counter = newCounter();

socket = websocketFsm();

socket.on("graph.new-node", function(n) {
  return graph.handle("graph.new-node", n);
});

socket.on("graph.new-edge", function(n) {
  return graph.handle("graph.new-edge", n);
});

socket.on("graph.initialized", function(n) {
  return graph.handle("graph.initialized", n);
});

socket.on("node.message", function(n) {
  return counter.handle("node.message", n);
});

counter.on("stats", function(stats) {
  return graph.handle("update-stats", stats);
});

i = setInterval((function() {
  var e;
  try {
    counter.handle("calculate");
    return graph.handle("render");
  } catch (_error) {
    e = _error;
    return console.log(e.stack);
  }
}), 200);

stop = function() {
  clearInterval(i);
  return socket.handle("close");
};
