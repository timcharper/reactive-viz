wsUri = "ws://localhost:8080/"

websocketFsm = () ->
  websocket = new WebSocket(wsUri)
  new machina.Fsm(
    initialState: "waiting"
    states:
      waiting:
        _onEnter: ->
          websocket.onopen = (evt) =>
            @handle "open", evt

          websocket.onclose = (evt) =>
            @handle "close", evt

          websocket.onmessage = (evt) =>
            @handle "message", evt

          websocket.onerror = (evt) ->
            @handle "error", evt

        open: (evt) ->
          console.log evt
          console.log "connected!"
          @transition("open")
        "*": (payload, a) ->
          console.log("deferring", payload, a)
          @deferUntilTransition()
      open: 
        "publish": ([topic, payload]) ->
          console.log "publishing", topic, payload
          websocket.send(JSON.stringify([topic, payload]))
        "*": (a, b, c) ->
          console.log "very thing", a, b, c
  )

socket = websocketFsm()
socket.handle("publish", ["get-data"])
socket.handle("publish", ["get-data"])

# Create the input graph
g = new dagreD3.graphlib.Graph().setGraph({}).setDefaultEdgeLabel(->
  {}
)

# Here we"re setting nodeclass, which is used by our custom drawNodes function
# below.
g.setNode 0,
  label: "TOP"
  class: "type-TOP"

g.setNode 1,
  label: "S"
  class: "type-S"

g.setNode 2,
  label: "NP"
  class: "type-NP"

g.setNode 3,
  label: "DT"
  class: "type-DT"

g.setNode 4,
  label: "This"
  class: "type-TK"

g.setNode 5,
  label: "VP"
  class: "type-VP"

g.setNode 6,
  label: "VBZ"
  class: "type-VBZ"

g.setNode 7,
  label: "is"
  class: "type-TK"

g.setNode 8,
  label: "NP"
  class: "type-NP"

g.setNode 9,
  label: "DT"
  class: "type-DT"

g.setNode 10,
  label: "an"
  class: "type-TK"

g.setNode 11,
  label: "NN"
  class: "type-NN"

g.setNode 12,
  label: "example"
  class: "type-TK"

g.setNode 13,
  label: "."
  class: "type-."

g.setNode 14,
  label: "sentence"
  class: "type-TK"

g.nodes().forEach (v) ->
  node = g.node(v)
  
  # Round the corners of the nodes
  node.rx = node.ry = 5
  return


# Set up edges, no special attributes.
g.setEdge 3, 4
g.setEdge 2, 3
g.setEdge 1, 2
g.setEdge 6, 7
g.setEdge 5, 6
g.setEdge 9, 10
g.setEdge 8, 9
g.setEdge 11, 12
g.setEdge 8, 11
g.setEdge 5, 8
g.setEdge 1, 5
g.setEdge 13, 14
g.setEdge 1, 13
g.setEdge 0, 1

# Create the renderer
render = new dagreD3.render()

# Set up an SVG group so that we can translate the final graph.
svg = d3.select("svg")
svgGroup = svg.append("g")

# Run the renderer. This is what draws the final graph.
render d3.select("svg g"), g

# Center the graph
xCenterOffset = (svg.attr("width") - g.graph().width) / 2
svgGroup.attr "transform", "translate(" + xCenterOffset + ", 20)"
svg.attr "height", g.graph().height + 40
