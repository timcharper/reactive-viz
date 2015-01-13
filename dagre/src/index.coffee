wsUri = "ws://localhost:8080/"
runningWindowMs = 1000

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
            @handle "message", evt.data

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
        "close": ->
          websocket.close()
          @transition("close")
        "message": (data) ->
          [channel, payload] = JSON.parse(data)
          @emit(channel, payload)
      close:
        "close": -> # nothing
        "*": -> console.log("we're closed")
  )

socket = websocketFsm()
socket.handle("publish", ["ping"])
socket.handle("publish", ["ping"])


newGraph = () ->
  nodeIdToIndexMap = {}
  _nodeIndex = 0
  nodeIndex = (nodeId) ->
    if nodeIdToIndexMap[nodeId]?
      nodeIdToIndexMap[nodeId]
    else
      idx = _nodeIndex
      _nodeIndex += 1
      nodeIdToIndexMap[nodeId] = idx
    
  # Create the input graph
  g = new dagreD3.graphlib.Graph().setGraph({}).setDefaultEdgeLabel(->
    {}
  )

  # Create the renderer
  render = new dagreD3.render()

  # Set up an SVG group so that we can translate the final graph.
  svg = d3.select("svg")
  svgGroup = svg.select("g")

  doRender = () ->
    g.nodes().forEach (v) ->
      node = g.node(v)

      # Round the corners of the nodes
      node.rx = node.ry = 5
      return

    # render d3.select("svg g"), g
    svgGroup.call(render, g)


    # Center the graph
    xCenterOffset = (svg.attr("width") - g.graph().width) / 2
    svgGroup.attr "transform", "translate(" + xCenterOffset + ", 20)"
    svg.attr "height", g.graph().height + 40

  maxRate = 0
  calcMessageRate = (deliveries) ->
    border = (new Date) - runningWindowMs
    midPoint = (new Date) - (runningWindowMs / 2)
    while (deliveries[0] && deliveries[0] < border)
      deliveries.shift()
    if (deliveries.length == 0)
      return 0
    rate = (deliveries.length / runningWindowMs)
    if (rate > maxRate)
      maxRate = rate
    rate

  nodeHtml = (node) ->
    r = calcMessageRate(node.deliveries) * 1000
    html = "<div>";
    html += "<span class=node-name>#{node.name}</span>";
    html += "<span class=message-count>#{node.messageCount}</span>";
    html += "<span class=message-rate>#{r.toFixed(1)} / s</span>";
    html += "</div>";
    html

  nodes = {}
  registerNode = ({id, name}) -> 
    console.log("graph.new-node", id)
    nodes[id] = {id, name, messageCount: 0, deliveries: []}
    nodes[id].deliveries.name = name
    g.setNode nodeIndex(id), id: id, name: name, labelType: "html", label: nodeHtml(nodes[id]), class: "type-#{id}"

  registerEdge = ({from, to}) ->
    console.log("graph.new-edge", from, to)
    g.setEdge nodeIndex(from), nodeIndex(to)

  toggleStates = {}
  new machina.Fsm(
    initialState: "init"
    states:
      init:
        "graph.new-node": registerNode
        "graph.new-edge": registerEdge
        "graph.initialized": ->
          @transition("drawing")
        "*": (payload, a) ->
          console.log("unknown msg:", payload, a)
      drawing:
        _onEnter: ->
          try
            doRender()
          catch error
            console.log(error)
            console.log(error.stack)
        "graph.new-node": (data) ->
          registerNode(data)
          doRender()
        "graph.new-edge": (data) ->
          registerEdge(data)
          doRender()
        "node.message": ({nodeId}) ->
          idx = nodeIndex(nodeId)
          gNode = g.node(idx)
          gNode.changed = true
          g.setNode(idx, gNode)

          n = nodes[nodeId]
          n.messageCount += 1
          n.deliveries.push(new Date)
          # svgGroup.call(render, g)
          # animate a message being received here
        "render": ->
          g.nodes().forEach (idx) ->
            gNode = g.node(idx)
            node = nodes[gNode.id]
            rate = calcMessageRate(node.deliveries)
            # if (gNode.changed)
            #   gNode.changed = false
            #   gNode.phase = ((gNode.phase || 0) + 1) % 2
            #   gNode.class = "phase-#{gNode.phase}"
            gNode.label = nodeHtml(nodes[gNode.id])
            percent  = 255 - Math.round((rate / maxRate) * 255)
            gNode.style = "fill: RGB(#{percent},255,#{percent})"
            # node = g.node(v)

            # # Round the corners of the nodes
            # node.rx = node.ry = 5
          svgGroup.call(render, g)
        "*": (payload, a) ->
          console.log("unknown msg:", payload, a)

  )

graph = newGraph()

socket.on "graph.new-node", (n) -> graph.handle "graph.new-node", n
socket.on "graph.new-edge", (n) ->
  graph.handle "graph.new-edge", n
socket.on "graph.initialized", (n) -> graph.handle "graph.initialized", n
socket.on "node.message", (n) -> graph.handle "node.message", n
setInterval(
  (->
    try
      graph.handle("render")
    catch e
      console.log e
  ),
  100)
