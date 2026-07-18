(function initializeBrainAnimation(root, factory) {
  const api = factory();

  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }

  root.AntiScrollBrain = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createBrainApi() {
  "use strict";

  function createRandom(seed) {
    let value = seed >>> 0;
    return function random() {
      value += 0x6d2b79f5;
      let result = value;
      result = Math.imul(result ^ (result >>> 15), result | 1);
      result ^= result + Math.imul(result ^ (result >>> 7), result | 61);
      return ((result ^ (result >>> 14)) >>> 0) / 4294967296;
    };
  }

  function isInsideBrain(x, y) {
    const leftLobe = ((x - 0.37) / 0.37) ** 2 + ((y - 0.43) / 0.37) ** 2 < 1;
    const rightLobe = ((x - 0.65) / 0.38) ** 2 + ((y - 0.43) / 0.37) ** 2 < 1;
    const lowerMass = ((x - 0.54) / 0.37) ** 2 + ((y - 0.62) / 0.24) ** 2 < 1;
    const topNotch = ((x - 0.5) / 0.075) ** 2 + ((y - 0.08) / 0.09) ** 2 < 1;
    return (leftLobe || rightLobe || lowerMass) && !topNotch && y < 0.83;
  }

  function createBrainModel(seed = 87421) {
    const random = createRandom(seed);
    const nodes = [];
    let attempts = 0;

    while (nodes.length < 58 && attempts < 2000) {
      attempts += 1;
      const x = 0.1 + random() * 0.82;
      const y = 0.08 + random() * 0.75;

      if (!isInsideBrain(x, y)) {
        continue;
      }

      const hasNearbyNode = nodes.some(
        (node) => Math.hypot(node.x - x, node.y - y) < 0.065
      );
      if (hasNearbyNode) {
        continue;
      }

      nodes.push({
        x,
        y,
        phase: random() * Math.PI * 2,
        speed: 0.72 + random() * 0.85,
        radius: 1.5 + random() * 1.65
      });
    }

    const stemNodes = [
      { x: 0.55, y: 0.82 },
      { x: 0.57, y: 0.9 },
      { x: 0.585, y: 0.98 }
    ];
    for (const node of stemNodes) {
      nodes.push({
        ...node,
        phase: random() * Math.PI * 2,
        speed: 0.8 + random() * 0.6,
        radius: 1.8 + random()
      });
    }

    const edgeKeys = new Set();
    const edges = [];

    for (let sourceIndex = 0; sourceIndex < nodes.length; sourceIndex += 1) {
      const source = nodes[sourceIndex];
      const neighbors = nodes
        .map((node, index) => ({
          index,
          distance: Math.hypot(node.x - source.x, node.y - source.y)
        }))
        .filter(
          ({ index, distance }) =>
            index !== sourceIndex && distance < 0.28
        )
        .sort((a, b) => a.distance - b.distance)
        .slice(0, 4);

      for (const neighbor of neighbors) {
        const low = Math.min(sourceIndex, neighbor.index);
        const high = Math.max(sourceIndex, neighbor.index);
        const key = `${low}:${high}`;
        if (edgeKeys.has(key)) {
          continue;
        }

        edgeKeys.add(key);
        edges.push({
          from: low,
          to: high,
          curve: (random() - 0.5) * 0.09,
          phase: random() * Math.PI * 2
        });
      }
    }

    const pulses = Array.from({ length: 18 }, (_value, index) => ({
      edgeIndex: Math.floor(random() * edges.length),
      offset: random(),
      speed: 0.7 + random() * 1.2,
      warm: index % 5 === 0
    }));

    return { nodes, edges, pulses };
  }

  function getQuadraticPoint(start, control, end, progress) {
    const inverse = 1 - progress;
    return {
      x:
        inverse * inverse * start.x +
        2 * inverse * progress * control.x +
        progress * progress * end.x,
      y:
        inverse * inverse * start.y +
        2 * inverse * progress * control.y +
        progress * progress * end.y
    };
  }

  function createBrainAnimation(canvas, options = {}) {
    const context = canvas?.getContext?.("2d");
    if (!context) {
      return { destroy() {} };
    }

    const model = createBrainModel(options.seed || 87421);
    const reducedMotion =
      typeof window.matchMedia === "function" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const pointer = { x: 0, y: 0, targetX: 0, targetY: 0 };
    let width = 1;
    let height = 1;
    let frameId = 0;
    let destroyed = false;

    function mapPoint(point) {
      return {
        x: width * (0.04 + point.x * 0.92),
        y: height * (0.02 + point.y * 0.91)
      };
    }

    function getEdgeGeometry(edge) {
      const start = mapPoint(model.nodes[edge.from]);
      const end = mapPoint(model.nodes[edge.to]);
      const dx = end.x - start.x;
      const dy = end.y - start.y;
      const length = Math.max(1, Math.hypot(dx, dy));
      return {
        start,
        end,
        control: {
          x: (start.x + end.x) / 2 + (-dy / length) * edge.curve * width,
          y: (start.y + end.y) / 2 + (dx / length) * edge.curve * width
        }
      };
    }

    function drawBrainOutline(time) {
      const breathing = 0.5 + Math.sin(time * 0.00075) * 0.5;
      const gradient = context.createRadialGradient(
        width * 0.54,
        height * 0.46,
        width * 0.05,
        width * 0.54,
        height * 0.46,
        width * 0.48
      );
      gradient.addColorStop(0, `rgba(104, 245, 173, ${0.055 + breathing * 0.025})`);
      gradient.addColorStop(0.7, "rgba(35, 205, 170, 0.018)");
      gradient.addColorStop(1, "rgba(7, 11, 13, 0)");
      context.fillStyle = gradient;
      context.fillRect(0, 0, width, height);

      context.save();
      context.strokeStyle = `rgba(104, 245, 173, ${0.26 + breathing * 0.09})`;
      context.lineWidth = 1.35;
      context.shadowColor = "rgba(104, 245, 173, 0.45)";
      context.shadowBlur = 14 + breathing * 7;
      context.beginPath();
      context.moveTo(width * 0.18, height * 0.55);
      context.bezierCurveTo(width * 0.07, height * 0.5, width * 0.07, height * 0.34, width * 0.17, height * 0.28);
      context.bezierCurveTo(width * 0.11, height * 0.16, width * 0.25, height * 0.07, width * 0.38, height * 0.12);
      context.bezierCurveTo(width * 0.44, height * 0.035, width * 0.57, height * 0.045, width * 0.62, height * 0.12);
      context.bezierCurveTo(width * 0.76, height * 0.055, width * 0.9, height * 0.16, width * 0.87, height * 0.29);
      context.bezierCurveTo(width * 0.97, height * 0.35, width * 0.94, height * 0.5, width * 0.86, height * 0.53);
      context.bezierCurveTo(width * 0.91, height * 0.66, width * 0.78, height * 0.73, width * 0.68, height * 0.69);
      context.bezierCurveTo(width * 0.62, height * 0.78, width * 0.49, height * 0.77, width * 0.43, height * 0.7);
      context.bezierCurveTo(width * 0.31, height * 0.78, width * 0.15, height * 0.69, width * 0.18, height * 0.55);
      context.stroke();

      context.strokeStyle = "rgba(104, 245, 173, 0.11)";
      context.shadowBlur = 0;
      context.beginPath();
      context.moveTo(width * 0.51, height * 0.13);
      context.bezierCurveTo(width * 0.46, height * 0.28, width * 0.55, height * 0.4, width * 0.49, height * 0.7);
      context.stroke();
      context.restore();
    }

    function draw(time) {
      if (destroyed) {
        return;
      }

      context.clearRect(0, 0, width, height);
      pointer.x += (pointer.targetX - pointer.x) * 0.035;
      pointer.y += (pointer.targetY - pointer.y) * 0.035;

      context.save();
      context.translate(pointer.x, pointer.y);
      drawBrainOutline(time);

      for (const edge of model.edges) {
        const geometry = getEdgeGeometry(edge);
        const signal = 0.5 + Math.sin(time * 0.0011 + edge.phase) * 0.5;
        context.beginPath();
        context.moveTo(geometry.start.x, geometry.start.y);
        context.quadraticCurveTo(
          geometry.control.x,
          geometry.control.y,
          geometry.end.x,
          geometry.end.y
        );
        context.strokeStyle = `rgba(71, 220, 190, ${0.15 + signal * 0.19})`;
        context.lineWidth = 0.78 + signal * 0.42;
        context.stroke();
      }

      for (let index = 0; index < model.nodes.length; index += 1) {
        const node = model.nodes[index];
        const point = mapPoint(node);
        const activity = 0.5 + Math.sin(time * 0.0018 * node.speed + node.phase) * 0.5;
        const firing = activity > 0.92;
        const radius = node.radius + activity * 1.5;
        const color = firing && index % 7 === 0 ? "255, 166, 94" : "104, 245, 173";

        context.beginPath();
        context.arc(point.x, point.y, radius, 0, Math.PI * 2);
        context.fillStyle = `rgba(${color}, ${0.56 + activity * 0.4})`;
        context.shadowColor = `rgba(${color}, 0.8)`;
        context.shadowBlur = 7 + activity * 13;
        context.fill();

        context.beginPath();
        context.arc(point.x, point.y, Math.max(0.7, radius * 0.36), 0, Math.PI * 2);
        context.fillStyle = "rgba(244, 255, 250, 0.92)";
        context.shadowBlur = 0;
        context.fill();
      }

      for (const pulse of model.pulses) {
        const edge = model.edges[pulse.edgeIndex];
        const geometry = getEdgeGeometry(edge);
        const progress = (time * 0.00012 * pulse.speed + pulse.offset) % 1;
        const point = getQuadraticPoint(
          geometry.start,
          geometry.control,
          geometry.end,
          progress
        );
        const color = pulse.warm ? "255, 166, 94" : "133, 255, 197";

        context.beginPath();
        context.arc(point.x, point.y, pulse.warm ? 2.5 : 1.9, 0, Math.PI * 2);
        context.fillStyle = `rgba(${color}, 0.96)`;
        context.shadowColor = `rgba(${color}, 0.9)`;
        context.shadowBlur = pulse.warm ? 16 : 11;
        context.fill();
      }

      context.restore();
      context.shadowBlur = 0;

      if (!reducedMotion) {
        frameId = window.requestAnimationFrame(draw);
      }
    }

    function resize() {
      const bounds = canvas.getBoundingClientRect();
      const density = Math.min(2, window.devicePixelRatio || 1);
      width = Math.max(1, bounds.width);
      height = Math.max(1, bounds.height);
      canvas.width = Math.round(width * density);
      canvas.height = Math.round(height * density);
      context.setTransform(density, 0, 0, density, 0, 0);

      if (reducedMotion) {
        draw(1600);
      }
    }

    function handlePointerMove(event) {
      const bounds = canvas.getBoundingClientRect();
      pointer.targetX = ((event.clientX - bounds.left) / bounds.width - 0.5) * 9;
      pointer.targetY = ((event.clientY - bounds.top) / bounds.height - 0.5) * 7;
    }

    function resetPointer() {
      pointer.targetX = 0;
      pointer.targetY = 0;
    }

    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    canvas.addEventListener("pointermove", handlePointerMove);
    canvas.addEventListener("pointerleave", resetPointer);
    resize();
    draw(0);

    return {
      destroy() {
        destroyed = true;
        observer.disconnect();
        canvas.removeEventListener("pointermove", handlePointerMove);
        canvas.removeEventListener("pointerleave", resetPointer);
        window.cancelAnimationFrame(frameId);
      }
    };
  }

  return Object.freeze({
    createBrainAnimation,
    createBrainModel,
    getQuadraticPoint,
    isInsideBrain
  });
});
