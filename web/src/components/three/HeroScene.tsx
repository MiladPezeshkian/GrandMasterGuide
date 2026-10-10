"use client";

import { ContactShadows, Environment, Float, Lightformer, RoundedBox, useGLTF } from "@react-three/drei";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import { Suspense, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import * as THREE from "three";
import { fileRank, trackPieces, type BoardPiece } from "../board/model";
import { pieceGeometries, pieceMaterials } from "./pieces";

// The Italian Game, played over and over on the hero board.
const LINE = [
  "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w",
  "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b",
  "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w",
  "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b",
  "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w",
  "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b",
  "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w",
  "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R b",
  "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R w",
  "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2BPP3/2P2N2/PP3PPP/RNBQK2R b",
];

function world(square: string): [number, number] {
  const [f, r] = fileRank(square);
  return [f - 3.5, 3.5 - r];
}

/**
 * [side]: where the scene sits in a full-width canvas ("left" / "right" of the text), or "center"
 * when the canvas is a block of its own (phones).
 */
export default function HeroScene({ theme, side }: { theme: "zorix" | "sky"; side: "left" | "right" | "center" }) {
  return (
    <Canvas dpr={[1, 1.75]} camera={{ fov: 30, position: side === "center" ? [0, 13, 22] : [0, 10, 18.5] }} gl={{ antialias: true, alpha: true }} shadows>
      <Suspense fallback={null}>
        <Stage theme={theme} side={side} />
      </Suspense>
    </Canvas>
  );
}

function Stage({ theme, side }: { theme: "zorix" | "sky"; side: "left" | "right" | "center" }) {
  const s = side === "left" ? -1 : side === "right" ? 1 : 0;
  const boardX = s * 5.0;
  const kingX = side === "center" ? 3.6 : s * 2.2;
  const accent = theme === "zorix" ? "#ff2a35" : "#3fb6ff";
  const group = useRef<THREE.Group>(null);
  const { pointer } = useThree();
  useFrame((state, dt) => {
    const g = group.current;
    if (!g) return;
    // Gentle parallax with the pointer, and a slow idle sway.
    const t = state.clock.elapsedTime;
    const ry = (s === 0 ? -0.35 : -s * 0.38) + pointer.x * 0.12 + Math.sin(t * 0.25) * 0.05;
    const rx = pointer.y * -0.05;
    g.rotation.y += (ry - g.rotation.y) * Math.min(1, dt * 2.5);
    g.rotation.x += (rx - g.rotation.x) * Math.min(1, dt * 2.5);
  });
  return (
    <>
      <ambientLight intensity={theme === "zorix" ? 0.3 : 0.7} />
      <directionalLight position={[6, 12, 8]} intensity={2.2} castShadow shadow-mapSize={[1024, 1024]} shadow-camera-left={-8} shadow-camera-right={8} shadow-camera-top={8} shadow-camera-bottom={-8} />
      <pointLight position={[-6, 4, -5]} intensity={90} color={accent} distance={28} />
      <pointLight position={[7, 3, 3]} intensity={14} color={accent} distance={18} />
      <Environment resolution={128} frames={1}>
        <Lightformer intensity={2.2} position={[0, 7, 7]} scale={[12, 3, 1]} />
        <Lightformer intensity={1.6} position={[-7, 3, 0]} rotation-y={Math.PI / 2} scale={[8, 3, 1]} color={accent} />
        <Lightformer intensity={0.9} position={[7, 4, -3]} rotation-y={-Math.PI / 2} scale={[8, 3, 1]} />
      </Environment>

      <group position={[boardX, -0.6, -1.2]} scale={s === 0 ? 1 : 0.82}>
        <group ref={group}>
          <HeroBoard theme={theme} />
        </group>
      </group>
      <Float speed={1.6} rotationIntensity={0.25} floatIntensity={0.6}>
        <King position={[kingX, -0.9, side === "center" ? 4.2 : 4.6]} />
      </Float>
      <ContactShadows position={[boardX, -1.02, -1.2]} opacity={theme === "zorix" ? 0.7 : 0.35} scale={22} blur={2.6} far={6} />
    </>
  );
}

function HeroBoard({ theme }: { theme: "zorix" | "sky" }) {
  const [step, setStep] = useState(0);
  const [pieces, setPieces] = useState<BoardPiece[]>([]);
  const geos = useMemo(() => pieceGeometries(), []);
  const mats = useMemo(() => pieceMaterials(theme), [theme]);

  useEffect(() => {
    const id = setInterval(() => setStep((s) => (s + 1) % (LINE.length + 2)), 1500);
    return () => clearInterval(id);
  }, []);
  useEffect(() => {
    const fen = LINE[Math.min(step, LINE.length - 1)];
    setPieces((prev) => trackPieces(step === 0 ? [] : prev, fen));
  }, [step]);

  const squares = useRef<THREE.InstancedMesh>(null);
  useLayoutEffect(() => {
    const m = squares.current;
    if (!m) return;
    const o = new THREE.Object3D();
    const light = new THREE.Color(theme === "zorix" ? "#d9d4cb" : "#f2f7fb");
    const dark = new THREE.Color(theme === "zorix" ? "#3b0c10" : "#7fb6dc");
    for (let i = 0; i < 64; i++) {
      const f = i % 8, r = Math.floor(i / 8);
      o.position.set(f - 3.5, -0.05, 3.5 - r);
      o.updateMatrix();
      m.setMatrixAt(i, o.matrix);
      m.setColorAt(i, (f + r) % 2 === 1 ? light : dark);
    }
    m.instanceMatrix.needsUpdate = true;
    if (m.instanceColor) m.instanceColor.needsUpdate = true;
  }, [theme]);

  return (
    <group>
      <RoundedBox args={[9.4, 0.46, 9.4]} radius={0.18} smoothness={4} position={[0, -0.29, 0]} receiveShadow castShadow>
        <meshPhysicalMaterial color={theme === "zorix" ? "#0f0f13" : "#ffffff"} roughness={0.22} metalness={theme === "zorix" ? 0.55 : 0.05} clearcoat={1} clearcoatRoughness={0.1} />
      </RoundedBox>
      <instancedMesh ref={squares} args={[undefined, undefined, 64]} receiveShadow>
        <boxGeometry args={[1, 0.1, 1]} />
        <meshPhysicalMaterial roughness={0.3} clearcoat={0.6} clearcoatRoughness={0.25} />
      </instancedMesh>
      {pieces.map((p) => (
        <HeroPiece key={p.id} piece={p} geometry={geos[p.kind]} material={p.color === "white" ? mats.white : mats.black} />
      ))}
    </group>
  );
}

function HeroPiece({ piece, geometry, material }: { piece: BoardPiece; geometry: THREE.BufferGeometry; material: THREE.Material }) {
  const ref = useRef<THREE.Mesh>(null);
  const [x, z] = world(piece.square);
  const target = useMemo(() => new THREE.Vector3(x, 0, z), [x, z]);
  const from = useRef<THREE.Vector3 | null>(null);
  const t = useRef(1);
  useEffect(() => {
    const m = ref.current;
    if (!m) return;
    if (!from.current) {
      m.position.copy(target);
      from.current = target.clone();
      return;
    }
    from.current = m.position.clone();
    t.current = 0;
  }, [target]);
  useFrame((_, dt) => {
    const m = ref.current;
    if (!m || !from.current || t.current >= 1) return;
    t.current = Math.min(1, t.current + dt / 0.5);
    const e = 1 - Math.pow(1 - t.current, 3);
    m.position.lerpVectors(from.current, target, e);
    m.position.y = Math.sin(Math.PI * e) * 0.6;
  });
  return <mesh ref={ref} geometry={geometry} material={material} castShadow rotation-y={piece.color === "black" ? Math.PI : 0} />;
}

/** "The King — 3DDecember Day5" by Batuhan13 (CC BY 4.0), see /credits. */
function King(props: { position: [number, number, number] }) {
  const { scene } = useGLTF("/models/king.glb");
  const ref = useRef<THREE.Group>(null);
  const model = useMemo(() => {
    const s = scene.clone(true);
    const box = new THREE.Box3().setFromObject(s);
    const size = box.getSize(new THREE.Vector3());
    const center = box.getCenter(new THREE.Vector3());
    const scale = 3.1 / size.y;
    s.position.set(-center.x * scale, -box.min.y * scale, -center.z * scale);
    s.scale.setScalar(scale);
    s.traverse((o) => {
      if ((o as THREE.Mesh).isMesh) {
        o.castShadow = true;
        o.receiveShadow = true;
      }
    });
    return s;
  }, [scene]);
  useFrame((_, dt) => {
    if (ref.current) ref.current.rotation.y += dt * 0.35;
  });
  return (
    <group ref={ref} position={props.position}>
      <primitive object={model} />
    </group>
  );
}

useGLTF.preload("/models/king.glb");
