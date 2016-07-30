/*
 * Copyright 2016 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'require es6/symbol es6/util/makeiterator util/polyfill es6/weakmap';

$jscomp.polyfill('WeakSet', function(NativeWeakSet) {
  /**
   * Checks conformance of the existing WeakSet.
   * @return {boolean} True if the browser's implementation conforms.
   */
  function isConformant() {
    if (!NativeWeakSet || !Object.seal) return false;
    var x = Object.seal({});
    var y = Object.seal({});
    var set = new /** @type {function(new: WeakSet, !Array)} */ (NativeWeakSet)(
        [x]);
    if (!set.has(x) || set.has(y)) return false;
    set.delete(x);
    set.add(y);
    return !set.has(x) && set.has(y);
  }
  if (isConformant()) return NativeWeakSet;

  /**
   * @constructor
   * @template TYPE
   * @param {!Iterator<TYPE>|!Array<TYPE>|null=} opt_iterable
   */
  var PolyfillWeakSet = function(opt_iterable) {
    /** @private @const {!WeakMap<TYPE, boolean>} */
    this.map_ = new WeakMap();

    if (opt_iterable) {
      $jscomp.initSymbol();
      $jscomp.initSymbolIterator();
      var iter = $jscomp.makeIterator(opt_iterable);
      var entry;
      while (!(entry = iter.next()).done) {
        var item = entry.value;
        this.add(item);
      }
    }
  };

  /**
   * @param {TYPE} elem
   * @return {!PolyfillWeakSet<TYPE>}
   */
  PolyfillWeakSet.prototype.add = function(elem) {
    this.map_.set(elem, true);
    return this;
  };

  /**
   * @param {TYPE} elem
   * @return {boolean}
   */
  PolyfillWeakSet.prototype.has = function(elem) {
    return this.map_.has(elem);
  };

  /**
   * @param {TYPE} elem
   * @return {boolean}
   */
  PolyfillWeakSet.prototype.delete = function(elem) {
    return this.map_.delete(elem);
  };

  return PolyfillWeakSet;
}, 'es6-impl', 'es3');
