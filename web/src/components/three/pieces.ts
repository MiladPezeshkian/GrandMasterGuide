import * as THREE from "three";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";

/*
 * Staunton-style pieces made from profiles turned on a lathe (and an extruded head for the knight).
 * Units: one board square = 1. The geometries are built once and shared by every piece.
 */
export type PieceKind = "p" | "n" | "b" | "r" | "q" | "k";

type P = [number, number];

function arc(cy: number, r: number, from: number, to: number, steps = 10, cx = 0): P[] {
  const out: P[] = [];
  for (let i = 0; i <= steps; i++) {
    const a = from + ((to - from) * i) / steps;
    out.push([Math.max(0, cx + r * Math.cos(a)), cy + r * Math.sin(a)]);
  }
  return out;
}

const BASE: P[] = [
  [0, 0],
  [0.36, 0],
  [0.375, 0.02],
  [0.375, 0.07],
  [0.345, 0.095],
  [0.31, 0.105],
  [0.315, 0.135],
  [0.29, 0.155],
  [0.25, 0.17],
];

function lathe(points: P[], segments = 40) {
  const g = new THREE.LatheGeometry(
    points.map(([x, y]) => new THREE.Vector2(x, y)),
    segments,
  );
  g.computeVertexNormals();
  return g;
}

function pawn() {
  return lathe([
    ...BASE,
    [0.2, 0.2],
    [0.155, 0.3],
    [0.13, 0.4],
    [0.19, 0.43],
    [0.195, 0.46],
    [0.12, 0.48],
    ...arc(0.6, 0.15, -Math.PI / 2 + 0.5, Math.PI / 2, 14),
  ]);
}

function rook() {
  const body = lathe([
    ...BASE,
    [0.235, 0.21],
    [0.2, 0.32],
    [0.19, 0.55],
    [0.23, 0.6],
    [0.255, 0.63],
    [0.255, 0.78],
    [0.185, 0.78],
    [0.185, 0.7],
    [0, 0.7],
  ]);
  const parts: THREE.BufferGeometry[] = [body];
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    const c = new THREE.BoxGeometry(0.075, 0.08, 0.09);
    c.translate(0, 0.82, 0.215);
    c.rotateY(a);
    parts.push(c.toNonIndexed() as THREE.BufferGeometry);
  }
  return merged(parts);
}

function bishop() {
  const body = lathe([
    ...BASE,
    [0.205, 0.21],
    [0.15, 0.36],
    [0.115, 0.5],
    [0.2, 0.535],
    [0.205, 0.565],
    [0.12, 0.585],
    [0.15, 0.63],
    [0.165, 0.69],
    [0.155, 0.76],
    [0.12, 0.83],
    [0.06, 0.88],
    [0.035, 0.9],
    ...arc(0.94, 0.045, -Math.PI / 2, Math.PI / 2, 8),
  ]);
  return body;
}

function queen() {
  const body = lathe([
    ...BASE,
    [0.225, 0.21],
    [0.16, 0.36],
    [0.12, 0.6],
    [0.21, 0.64],
    [0.215, 0.67],
    [0.13, 0.7],
    [0.17, 0.78],
    [0.235, 0.88],
    [0.2, 0.9],
    [0.12, 0.92],
    [0.06, 0.95],
    ...arc(1.0, 0.055, -Math.PI / 2, Math.PI / 2, 8),
  ]);
  const parts: THREE.BufferGeometry[] = [body];
  for (let i = 0; i < 9; i++) {
    const a = (i / 9) * Math.PI * 2;
    const ball = new THREE.SphereGeometry(0.035, 10, 8);
    ball.translate(Math.cos(a) * 0.225, 0.905, Math.sin(a) * 0.225);
    parts.push(ball);
  }
  return merged(parts);
}

function king() {
  const body = lathe([
    ...BASE,
    [0.23, 0.21],
    [0.165, 0.38],
    [0.125, 0.64],
    [0.215, 0.68],
    [0.22, 0.71],
    [0.135, 0.74],
    [0.18, 0.84],
    [0.22, 0.93],
    [0.2, 0.95],
    [0.1, 0.97],
    [0.05, 1.0],
    [0, 1.0],
  ]);
  const v = new THREE.BoxGeometry(0.06, 0.2, 0.06);
  v.translate(0, 1.09, 0);
  const h = new THREE.BoxGeometry(0.17, 0.055, 0.06);
  h.translate(0, 1.11, 0);
  return merged([body, v, h]);
}

function knight() {
  const base = lathe([...BASE, [0.22, 0.2], [0.2, 0.24], [0, 0.24]]);
  const s = new THREE.Shape();
  s.moveTo(-0.2, 0.2);
  s.lineTo(0.22, 0.2);
  s.quadraticCurveTo(0.25, 0.38, 0.1, 0.5);
  s.lineTo(0.25, 0.6);
  s.quadraticCurveTo(0.32, 0.66, 0.27, 0.74);
  s.quadraticCurveTo(0.17, 0.8, 0.07, 0.84);
  s.lineTo(0.05, 0.94);
  s.lineTo(-0.02, 0.87);
  s.quadraticCurveTo(-0.22, 0.82, -0.23, 0.58);
  s.quadraticCurveTo(-0.25, 0.36, -0.2, 0.2);
  const head = new THREE.ExtrudeGeometry(s, {
    depth: 0.16,
    bevelEnabled: true,
    bevelThickness: 0.04,
    bevelSize: 0.035,
    bevelSegments: 4,
    curveSegments: 16,
  });
  head.translate(0, 0, -0.08);
  // Facing +x; the caller turns it toward the opponent.
  head.rotateY(Math.PI / 2);
  head.computeVertexNormals();
  return merged([base, head]);
}

function merged(parts: THREE.BufferGeometry[]) {
  const ready = parts.map((g) => {
    const n = g.index ? g.toNonIndexed() : g;
    n.deleteAttribute("uv");
    return n;
  });
  const m = mergeGeometries(ready, false)!;
  m.computeVertexNormals();
  return m;
}

let cache: Record<PieceKind, THREE.BufferGeometry> | null = null;

/** Shared geometries of the six piece kinds. */
export function pieceGeometries(): Record<PieceKind, THREE.BufferGeometry> {
  if (!cache) {
    cache = { p: pawn(), n: knight(), b: bishop(), r: rook(), q: queen(), k: king() };
    for (const g of Object.values(cache)) {
      if (g.getAttribute("uv")) g.deleteAttribute("uv");
      g.computeBoundingSphere();
    }
  }
  return cache;
}

/** Ivory and obsidian, polished. */
export function pieceMaterials(theme: "zorix" | "sky") {
  const white = new THREE.MeshPhysicalMaterial({
    color: new THREE.Color("#f4efe6"),
    roughness: 0.32,
    metalness: 0.0,
    clearcoat: 0.9,
    clearcoatRoughness: 0.18,
    sheen: 0.4,
    sheenColor: new THREE.Color("#ffffff"),
  });
  const black = new THREE.MeshPhysicalMaterial({
    color: new THREE.Color(theme === "zorix" ? "#141418" : "#1d2a38"),
    roughness: 0.22,
    metalness: 0.25,
    clearcoat: 1,
    clearcoatRoughness: 0.08,
  });
  return { white, black };
}
