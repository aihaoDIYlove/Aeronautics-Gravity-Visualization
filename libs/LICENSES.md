# Third-party Libraries in `libs/`

These jars are extracted from upstream mods' jarjar bundles for **compile-time
use only**. They are **NOT** packaged into the final mod jar at runtime —
runtime provisioning comes from the parent mods' jarjar loaders (see
`build.gradle` for the `implementation` / `compileOnly` declarations).

| File | Upstream | License |
|---|---|---|
| `sable-companion.jar` | [ryanhcode/sable](https://github.com/ryanhcode/sable) | PolyForm Shield 1.0.0 |
| `simulated.jar` | [Creators-of-Aeronautics/Simulated-Project](https://github.com/Creators-of-Aeronautics/Simulated-Project) | MIT |
| `ponder.jar` | [Creators-of-Create/Create](https://github.com/Creators-of-Create/Create) (Catnip jarjar) | MIT |
| `flywheel.jar` | [Creators-of-Create/Create](https://github.com/Creators-of-Create/Create) (Flywheel jarjar) | MIT |

---

## PolyForm Shield 1.0.0 — applies to `sable-companion.jar`

License text: <https://polyformproject.org/licenses/shield/1.0.0>

Copyright (c) Sable contributors

Per the **Notices** clause of PolyForm Shield 1.0.0, anyone who receives any
part of this software must also receive a copy of these terms or the URL above.

## MIT License — applies to `simulated.jar`, `ponder.jar`, `flywheel.jar`

License text: <https://opensource.org/license/mit>

```
MIT License

Copyright (c) The Create Team / The Creators of Create
Copyright (c) The Simulated Team / The Creators of Aeronautics

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
