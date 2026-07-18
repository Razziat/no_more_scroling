"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  createBrainModel,
  getQuadraticPoint,
  isInsideBrain
} = require("../src/content/brain-animation.js");

test("génère un réseau neuronal dense et déterministe", () => {
  const first = createBrainModel(87421);
  const second = createBrainModel(87421);

  assert.deepEqual(first, second);
  assert.equal(first.nodes.length, 61);
  assert.ok(first.edges.length >= 140);
  assert.equal(first.pulses.length, 18);
});

test("place les neurones principaux dans la silhouette du cerveau", () => {
  const model = createBrainModel();

  for (const node of model.nodes.slice(0, 58)) {
    assert.equal(isInsideBrain(node.x, node.y), true);
  }
});

test("ne crée que des connexions et impulsions valides", () => {
  const model = createBrainModel();

  for (const edge of model.edges) {
    assert.ok(edge.from >= 0 && edge.from < model.nodes.length);
    assert.ok(edge.to >= 0 && edge.to < model.nodes.length);
    assert.notEqual(edge.from, edge.to);
  }

  for (const pulse of model.pulses) {
    assert.ok(pulse.edgeIndex >= 0 && pulse.edgeIndex < model.edges.length);
  }
});

test("interpole correctement une impulsion sur une courbe", () => {
  const start = { x: 0, y: 0 };
  const control = { x: 5, y: 10 };
  const end = { x: 10, y: 0 };

  assert.deepEqual(getQuadraticPoint(start, control, end, 0), start);
  assert.deepEqual(getQuadraticPoint(start, control, end, 1), end);
  assert.deepEqual(getQuadraticPoint(start, control, end, 0.5), {
    x: 5,
    y: 5
  });
});
