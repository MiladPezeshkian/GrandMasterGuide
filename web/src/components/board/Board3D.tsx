"use client";

import { Environment, Lightformer, OrbitControls, RoundedBox, Text } from "@react-three/drei";
import { Canvas, useFrame, useThree, type ThreeEvent } from "@react-three/fiber";
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import * as THREE from "three";
import { pieceGeometries, pieceMaterials } from "../three/pieces";
import { BOARD_COLORS, fileRank, FILES, kingSquare, squareName, trackPieces, turnOf, type BoardPiece, type BoardProps } from "./model";
import { PromotionDialog } from "./PromotionDialog";
import { useInteraction } from "./useInteraction";

/** Board square -> world position (white's first rank nearest the default camera). */
function world(square: string): [number, number] {
  const [f, r] = fileRank(square);
  return [f - 3.5, 3.5 - r];
}

/** The 3D board: polished pieces on a lacquered board; tap a piece, then a square. Drag to turn the view. */
export function Board3D(props: BoardProps & { appTheme?: "zorix" | "sky" }) {
  const { appTheme = "zorix" } = props;
  return (
    <div dir="ltr" className="relative aspect-square w-full touch-none select-none overflow-hidden rounded-2xl" style={{ background: appTheme === "zorix" ? "radial-gradient(ellipse at 50% 30%, #1d1d26 0%, #08080b 75%)" : "radial-gradient(ellipse at 50% 30%, #ffffff 0%, #d9eaf8 80%)" }}>
      <Canvas shadows dpr={[1, 2]} frameloop="demand" camera={{ fov: 32, position: [0, 15.2, 10.4], near: 0.5, far: 90 }} gl={{ antialias: true, powerPreference: "high-performance" }}>
        <Scene {...props} appTheme={appTheme} />
      </Canvas>
      {props.promotion && (
        <PromotionDialog color={props.promotion.side ?? (props.promotion.to[1] === "8" ? "white" : "black")} onPick={(p) => props.onPromote?.(p)} />
      )}
    </div>
  );
}

function Scene(props: BoardProps & { appTheme: "zorix" | "sky" }) {
  const { fen, orientation, lastMove, check, arrows = [], highlights = {}, stars = [], hint, showLegal = true, coordinates = true, theme = "classic", appTheme } = props;
  const { selected, targets, tap, canPick, board } = useInteraction(props);
  const [pieces, setPieces] = useState<BoardPiece[]>([]);
  const invalidate = useThree((s) => s.invalidate);
  const flipped = orientation === "black";
  const geos = useMemo(() => pieceGeometries(), []);
  const mats = useMemo(() => pieceMaterials(appTheme), [appTheme]);
  const colors = BOARD_COLORS[theme];

  useEffect(() => {
    setPieces((prev) => trackPieces(prev, fen));
    invalidate();
  }, [fen, invalidate]);
  useEffect(() => invalidate(), [selected, targets, lastMove, check, arrows, highlights, stars, hint, theme, appTheme, orientation, invalidate]);

  const last = lastMove ? [lastMove.slice(0, 2), lastMove.slice(2, 4)] : [];
  const checkSquare = check ? kingSquare(fen, turnOf(fen)) : null;
  const marks: [string, string][] = [];
  for (const sq of last) marks.push([sq, "#ffd54f"]);
  for (const [sq, c] of Object.entries(highlights)) marks.push([sq, c]);
  if (hint) marks.push([hint, "#2fd27c"]);
  if (selected) marks.push([selected, "#1ea0ff"]);

  const onSquare = (e: ThreeEvent<PointerEvent>) => {
    e.stopPropagation();
    if (e.instanceId == null) return;
    const f = e.instanceId % 8, r = Math.floor(e.instanceId / 8);
    tap(squareName(f, r));
  };

  return (
    <>
      <color attach="background" args={[appTheme === "zorix" ? "#0a0a0d" : "#e8f2fb"]} />
      <fog attach="fog" args={[appTheme === "zorix" ? "#0a0a0d" : "#e8f2fb", 18, 34]} />
      <ambientLight intensity={appTheme === "zorix" ? 0.35 : 0.6} />
      <directionalLight
        position={[5, 12, 7]}
        intensity={2.1}
        castShadow
        shadow-mapSize={[1024, 1024]}
        shadow-camera-left={-6}
        shadow-camera-right={6}
        shadow-camera-top={6}
        shadow-camera-bottom={-6}
        shadow-bias={-0.0004}
      />
      <pointLight position={[-7, 5, -6]} intensity={appTheme === "zorix" ? 60 : 25} color={appTheme === "zorix" ? "#ff2a35" : "#3fb6ff"} distance={30} />
      <Environment resolution={128} frames={1}>
        <Lightformer intensity={2} position={[0, 6, 6]} scale={[10, 2, 1]} />
        <Lightformer intensity={1.2} position={[-6, 3, 0]} rotation-y={Math.PI / 2} scale={[6, 2, 1]} color={appTheme === "zorix" ? "#ff4b55" : "#7fd0ff"} />
        <Lightformer intensity={0.8} position={[6, 3, -2]} rotation-y={-Math.PI / 2} scale={[6, 2, 1]} />
      </Environment>

      <group rotation-y={flipped ? Math.PI : 0}>
        {/* Frame */}
        <RoundedBox args={[9.3, 0.42, 9.3]} radius={0.16} smoothness={4} position={[0, -0.27, 0]} receiveShadow>
          <meshPhysicalMaterial color={appTheme === "zorix" ? "#121216" : "#f7fbff"} roughness={0.28} metalness={appTheme === "zorix" ? 0.4 : 0.05} clearcoat={1} clearcoatRoughness={0.15} />
        </RoundedBox>

        <Squares light={colors.light} dark={colors.dark} onPointerDown={onSquare} />

        {/* Square highlights */}
        {marks.map(([sq, c], i) => {
          const [x, z] = world(sq);
          return (
            <mesh key={`m${i}-${sq}`} position={[x, 0.006, z]} rotation-x={-Math.PI / 2} raycast={() => null}>
              <planeGeometry args={[1, 1]} />
              <meshBasicMaterial color={c} transparent opacity={0.45} depthWrite={false} />
            </mesh>
          );
        })}
        {checkSquare && <CheckGlow square={checkSquare} />}

        {/* Legal moves */}
        {showLegal &&
          targets.map((sq) => {
            const [x, z] = world(sq);
            const occupied = board.has(sq);
            return (
              <mesh key={`t-${sq}`} position={[x, 0.012, z]} rotation-x={-Math.PI / 2} raycast={() => null}>
                {occupied ? <ringGeometry args={[0.38, 0.47, 40]} /> : <circleGeometry args={[0.15, 32]} />}
                <meshBasicMaterial color={appTheme === "zorix" ? "#ff4b55" : "#0b8bd9"} transparent opacity={0.85} depthWrite={false} />
              </mesh>
            );
          })}

        {stars.map((sq) => (
          <Star key={`s-${sq}`} square={sq} />
        ))}

        {arrows.map((a, i) => (
          <Arrow3D key={`a${i}`} from={a.from} to={a.to} color={a.color ?? "#2fd27c"} />
        ))}

        {pieces.map((p) => (
          <Piece
            key={p.id}
            piece={p}
            geometry={geos[p.kind]}
            material={p.color === "white" ? mats.white : mats.black}
            onPick={(e) => {
              e.stopPropagation();
              tap(p.square);
            }}
            hoverable={canPick(p.square) && !props.onSquare}
          />
        ))}
      </group>

      {coordinates && <Coordinates flipped={flipped} color={appTheme === "zorix" ? "#8b8b97" : "#4f6377"} />}

      <OrbitControls
        enablePan={false}
        enableDamping
        dampingFactor={0.12}
        minDistance={10}
        maxDistance={28}
        minPolarAngle={0.1}
        maxPolarAngle={1.18}
        target={[0, 0, 0.55]}
        makeDefault
      />
    </>
  );
}

function Squares({ light, dark, onPointerDown }: { light: string; dark: string; onPointerDown: (e: ThreeEvent<PointerEvent>) => void }) {
  const ref = useRef<THREE.InstancedMesh>(null);
  useLayoutEffect(() => {
    const m = ref.current;
    if (!m) return;
    const o = new THREE.Object3D();
    const cl = new THREE.Color(light), cd = new THREE.Color(dark);
    for (let i = 0; i < 64; i++) {
      const f = i % 8, r = Math.floor(i / 8);
      o.position.set(f - 3.5, -0.05, 3.5 - r);
      o.updateMatrix();
      m.setMatrixAt(i, o.matrix);
      m.setColorAt(i, (f + r) % 2 === 1 ? cl : cd);
    }
    m.instanceMatrix.needsUpdate = true;
    if (m.instanceColor) m.instanceColor.needsUpdate = true;
  }, [light, dark]);
  return (
    <instancedMesh ref={ref} args={[undefined, undefined, 64]} receiveShadow onPointerDown={onPointerDown}>
      <boxGeometry args={[1, 0.1, 1]} />
      <meshStandardMaterial roughness={0.42} metalness={0.05} />
    </instancedMesh>
  );
}

function Piece({ piece, geometry, material, onPick, hoverable }: { piece: BoardPiece; geometry: THREE.BufferGeometry; material: THREE.Material; onPick: (e: ThreeEvent<PointerEvent>) => void; hoverable: boolean }) {
  const ref = useRef<THREE.Mesh>(null);
  const [x, z] = world(piece.square);
  const target = useMemo(() => new THREE.Vector3(x, 0, z), [x, z]);
  const from = useRef<THREE.Vector3 | null>(null);
  const progress = useRef(1);
  const invalidate = useThree((s) => s.invalidate);
  const [hover, setHover] = useState(false);

  useEffect(() => {
    const m = ref.current;
    if (!m) return;
    if (from.current === null) {
      m.position.copy(target);
      from.current = target.clone();
      return;
    }
    from.current = m.position.clone();
    progress.current = 0;
    invalidate();
  }, [target, invalidate]);

  useFrame((_, dt) => {
    const m = ref.current;
    if (!m || !from.current) return;
    if (progress.current < 1) {
      progress.current = Math.min(1, progress.current + dt / 0.32);
      const t = 1 - Math.pow(1 - progress.current, 3);
      m.position.lerpVectors(from.current, target, t);
      const dist = from.current.distanceTo(target);
      m.position.y = Math.sin(Math.PI * t) * Math.min(0.9, 0.25 + dist * 0.12);
      invalidate();
    }
    const lift = hover ? 0.08 : 0;
    if (progress.current >= 1 && Math.abs(m.position.y - lift) > 0.001) {
      m.position.y += (lift - m.position.y) * Math.min(1, dt * 14);
      invalidate();
    }
  });

  return (
    <mesh
      ref={ref}
      geometry={geometry}
      material={material}
      castShadow
      rotation-y={piece.color === "black" ? Math.PI : 0}
      onPointerDown={onPick}
      onPointerOver={(e) => {
        e.stopPropagation();
        if (hoverable) {
          setHover(true);
          document.body.style.cursor = "pointer";
        }
      }}
      onPointerOut={() => {
        setHover(false);
        document.body.style.cursor = "";
      }}
    />
  );
}

function CheckGlow({ square }: { square: string }) {
  const [x, z] = world(square);
  const tex = useMemo(() => {
    const c = document.createElement("canvas");
    c.width = c.height = 128;
    const g = c.getContext("2d")!;
    const grad = g.createRadialGradient(64, 64, 4, 64, 64, 64);
    grad.addColorStop(0, "rgba(255,40,40,1)");
    grad.addColorStop(0.5, "rgba(255,40,40,0.5)");
    grad.addColorStop(1, "rgba(255,40,40,0)");
    g.fillStyle = grad;
    g.fillRect(0, 0, 128, 128);
    return new THREE.CanvasTexture(c);
  }, []);
  return (
    <mesh position={[x, 0.01, z]} rotation-x={-Math.PI / 2} raycast={() => null}>
      <planeGeometry args={[1.4, 1.4]} />
      <meshBasicMaterial map={tex} transparent depthWrite={false} />
    </mesh>
  );
}

function Star({ square }: { square: string }) {
  const [x, z] = world(square);
  const ref = useRef<THREE.Mesh>(null);
  const geo = useMemo(() => {
    const s = new THREE.Shape();
    for (let i = 0; i < 10; i++) {
      const r = i % 2 === 0 ? 0.3 : 0.13;
      const a = (i / 10) * Math.PI * 2 + Math.PI / 2;
      if (i === 0) s.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      else s.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    }
    s.closePath();
    const g = new THREE.ExtrudeGeometry(s, { depth: 0.08, bevelEnabled: true, bevelSize: 0.02, bevelThickness: 0.02, bevelSegments: 2 });
    g.center();
    return g;
  }, []);
  useFrame((_, dt) => {
    if (ref.current) ref.current.rotation.y += dt * 1.2;
  });
  return (
    <mesh ref={ref} geometry={geo} position={[x, 0.42, z]} castShadow raycast={() => null}>
      <meshPhysicalMaterial color="#ffc53d" metalness={0.6} roughness={0.25} emissive="#ff9a00" emissiveIntensity={0.25} />
    </mesh>
  );
}

function Arrow3D({ from, to, color }: { from: string; to: string; color: string }) {
  const [x1, z1] = world(from), [x2, z2] = world(to);
  const len = Math.hypot(x2 - x1, z2 - z1);
  const geo = useMemo(() => {
    const head = 0.42, w = 0.1, hw = 0.27;
    const s = new THREE.Shape();
    s.moveTo(0, -w);
    s.lineTo(len - head, -w);
    s.lineTo(len - head, -hw);
    s.lineTo(len, 0);
    s.lineTo(len - head, hw);
    s.lineTo(len - head, w);
    s.lineTo(0, w);
    s.closePath();
    return new THREE.ShapeGeometry(s);
  }, [len]);
  const angle = Math.atan2(-(z2 - z1), x2 - x1);
  return (
    <mesh geometry={geo} position={[x1, 0.03, z1]} rotation={[-Math.PI / 2, 0, angle]} raycast={() => null}>
      <meshBasicMaterial color={color} transparent opacity={0.85} depthWrite={false} side={THREE.DoubleSide} />
    </mesh>
  );
}

function Coordinates({ flipped, color }: { flipped: boolean; color: string }) {
  const s = flipped ? -1 : 1;
  const font = "/brand/orbitron-black.ttf";
  return (
    <group>
      {FILES.split("").map((f, i) => (
        <Text key={f} font={font} fontSize={0.26} color={color} position={[s * (i - 3.5), -0.05, s * 4.33]} rotation-x={-Math.PI / 2}>
          {f}
        </Text>
      ))}
      {Array.from({ length: 8 }, (_, r) => (
        <Text key={r} font={font} fontSize={0.26} color={color} position={[s * -4.33, -0.05, s * (3.5 - r)]} rotation-x={-Math.PI / 2}>
          {String(r + 1)}
        </Text>
      ))}
    </group>
  );
}
