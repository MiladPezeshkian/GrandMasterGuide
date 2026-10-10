import {
  fillArrayVali8eppxapiek4 as fillArrayVal,
  initMetadataForClassbxx6q50dy2s7 as initMetadataForClass,
  initMetadataForObject1cxne3s9w65el as initMetadataForObject,
  VOID7hggqo3abtya as VOID,
  toString30pk9tzaqopn as toString,
} from './kotlin-kotlin-stdlib.mjs';
//region block: imports
//endregion
//region block: pre-declaration
class atomicfu$AtomicRefArray$ref {
  constructor(size) {
    var tmp = this;
    var tmp_0 = 0;
    // Inline function 'kotlin.arrayOfNulls' call
    var tmp_1 = fillArrayVal(Array(size), null);
    while (tmp_0 < size) {
      tmp_1[tmp_0] = atomic$ref$1(null);
      tmp_0 = tmp_0 + 1 | 0;
    }
    tmp.ql_1 = tmp_1;
  }
  rl() {
    return this.ql_1.length;
  }
  atomicfu$get(index) {
    return this.ql_1[index];
  }
  get atomicfu$size() {
    return this.rl();
  }
}
class atomicfu$TraceBase {
  atomicfu$Trace$append$1(event) {
  }
  atomicfu$Trace$append$2(event1, event2) {
  }
  atomicfu$Trace$append$3(event1, event2, event3) {
  }
  atomicfu$Trace$append$4(event1, event2, event3, event4) {
  }
}
class None extends atomicfu$TraceBase {
  constructor() {
    None_instance = null;
    super();
    None_instance = this;
  }
}
class AtomicRef {
  constructor(value) {
    this.kotlinx$atomicfu$value = value;
  }
  sl(_set____db54di) {
    this.kotlinx$atomicfu$value = _set____db54di;
  }
  tl() {
    return this.kotlinx$atomicfu$value;
  }
  atomicfu$compareAndSet(expect, update) {
    if (!(this.kotlinx$atomicfu$value === expect))
      return false;
    this.kotlinx$atomicfu$value = update;
    return true;
  }
  atomicfu$getAndSet(value) {
    var oldValue = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = value;
    return oldValue;
  }
  toString() {
    return toString(this.kotlinx$atomicfu$value);
  }
}
class AtomicBoolean {
  constructor(value) {
    this.kotlinx$atomicfu$value = value;
  }
  ul(_set____db54di) {
    this.kotlinx$atomicfu$value = _set____db54di;
  }
  tl() {
    return this.kotlinx$atomicfu$value;
  }
  atomicfu$compareAndSet(expect, update) {
    if (!(this.kotlinx$atomicfu$value === expect))
      return false;
    this.kotlinx$atomicfu$value = update;
    return true;
  }
  atomicfu$getAndSet(value) {
    var oldValue = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = value;
    return oldValue;
  }
  toString() {
    return this.kotlinx$atomicfu$value.toString();
  }
}
class AtomicLong {
  constructor(value) {
    this.kotlinx$atomicfu$value = value;
  }
  vl(_set____db54di) {
    this.kotlinx$atomicfu$value = _set____db54di;
  }
  tl() {
    return this.kotlinx$atomicfu$value;
  }
  atomicfu$compareAndSet(expect, update) {
    if (!this.kotlinx$atomicfu$value.equals(expect))
      return false;
    this.kotlinx$atomicfu$value = update;
    return true;
  }
  atomicfu$getAndSet(value) {
    var oldValue = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = value;
    return oldValue;
  }
  atomicfu$getAndIncrement$long() {
    var _unary__edvuaz = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = _unary__edvuaz.o3();
    return _unary__edvuaz;
  }
  atomicfu$getAndDecrement$long() {
    var _unary__edvuaz = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = _unary__edvuaz.p3();
    return _unary__edvuaz;
  }
  atomicfu$getAndAdd$long(delta) {
    var oldValue = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = this.kotlinx$atomicfu$value.j3(delta);
    return oldValue;
  }
  atomicfu$addAndGet$long(delta) {
    this.kotlinx$atomicfu$value = this.kotlinx$atomicfu$value.j3(delta);
    return this.kotlinx$atomicfu$value;
  }
  atomicfu$incrementAndGet$long() {
    this.kotlinx$atomicfu$value = this.kotlinx$atomicfu$value.o3();
    return this.kotlinx$atomicfu$value;
  }
  atomicfu$decrementAndGet$long() {
    this.kotlinx$atomicfu$value = this.kotlinx$atomicfu$value.p3();
    return this.kotlinx$atomicfu$value;
  }
  toString() {
    return this.kotlinx$atomicfu$value.toString();
  }
}
class AtomicInt {
  constructor(value) {
    this.kotlinx$atomicfu$value = value;
  }
  wl(_set____db54di) {
    this.kotlinx$atomicfu$value = _set____db54di;
  }
  tl() {
    return this.kotlinx$atomicfu$value;
  }
  atomicfu$compareAndSet(expect, update) {
    if (!(this.kotlinx$atomicfu$value === expect))
      return false;
    this.kotlinx$atomicfu$value = update;
    return true;
  }
  atomicfu$getAndSet(value) {
    var oldValue = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = value;
    return oldValue;
  }
  atomicfu$getAndIncrement() {
    var _unary__edvuaz = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = _unary__edvuaz + 1 | 0;
    return _unary__edvuaz;
  }
  atomicfu$getAndDecrement() {
    var _unary__edvuaz = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = _unary__edvuaz - 1 | 0;
    return _unary__edvuaz;
  }
  atomicfu$getAndAdd(delta) {
    var oldValue = this.kotlinx$atomicfu$value;
    this.kotlinx$atomicfu$value = this.kotlinx$atomicfu$value + delta | 0;
    return oldValue;
  }
  atomicfu$addAndGet(delta) {
    this.kotlinx$atomicfu$value = this.kotlinx$atomicfu$value + delta | 0;
    return this.kotlinx$atomicfu$value;
  }
  atomicfu$incrementAndGet() {
    this.kotlinx$atomicfu$value = this.kotlinx$atomicfu$value + 1 | 0;
    return this.kotlinx$atomicfu$value;
  }
  atomicfu$decrementAndGet() {
    this.kotlinx$atomicfu$value = this.kotlinx$atomicfu$value - 1 | 0;
    return this.kotlinx$atomicfu$value;
  }
  toString() {
    return this.kotlinx$atomicfu$value.toString();
  }
}
//endregion
function atomicfu$AtomicRefArray$ofNulls(size) {
  return new atomicfu$AtomicRefArray$ref(size);
}
var None_instance;
function None_getInstance() {
  if (None_instance === VOID)
    new None();
  return None_instance;
}
function atomic$ref$1(initial) {
  return atomic$ref$(initial, None_getInstance());
}
function atomic$boolean$1(initial) {
  return atomic$boolean$(initial, None_getInstance());
}
function atomic$long$1(initial) {
  return atomic$long$(initial, None_getInstance());
}
function atomic$int$1(initial) {
  return atomic$int$(initial, None_getInstance());
}
function atomic$ref$(initial, trace) {
  trace = trace === VOID ? None_getInstance() : trace;
  return new AtomicRef(initial);
}
function atomic$boolean$(initial, trace) {
  trace = trace === VOID ? None_getInstance() : trace;
  return new AtomicBoolean(initial);
}
function atomic$long$(initial, trace) {
  trace = trace === VOID ? None_getInstance() : trace;
  return new AtomicLong(initial);
}
function atomic$int$(initial, trace) {
  trace = trace === VOID ? None_getInstance() : trace;
  return new AtomicInt(initial);
}
//region block: post-declaration
initMetadataForClass(atomicfu$AtomicRefArray$ref, 'AtomicArray');
initMetadataForClass(atomicfu$TraceBase, 'TraceBase');
initMetadataForObject(None, 'None');
initMetadataForClass(AtomicRef, 'AtomicRef');
initMetadataForClass(AtomicBoolean, 'AtomicBoolean');
initMetadataForClass(AtomicLong, 'AtomicLong');
initMetadataForClass(AtomicInt, 'AtomicInt');
//endregion
//region block: exports
export {
  atomicfu$AtomicRefArray$ofNulls as atomicfu$AtomicRefArray$ofNulls2kz3j9ehigwa3,
  atomic$boolean$1 as atomic$boolean$1iggki4z65a2h,
  atomic$long$1 as atomic$long$129k9zwo6n9ogd,
  atomic$ref$1 as atomic$ref$130aurmcwdfdf1,
  atomic$int$1 as atomic$int$11d5swdyn6j0pu,
};
//endregion

//# sourceMappingURL=kotlinx-atomicfu.mjs.map
