# Synthesis

Yosys flow that maps SystemVerilog onto Minecraft-buildable primitives.
The tech mapping originates from [V2MC](https://github.com/Kenny2github/V2MC),
with local fixes (dffe support, reset-value slicing, flattening, and build-time
validation). Full documentation lives in `show/docs/synthesis.md`.

In order to run, [OSS CAD Suite](https://github.com/YosysHQ/oss-cad-suite-build)
must be installed and added to the PATH. On my computer, I can just run
`source ~/Codes/oss-cad-suite/environment` because that's where I installed it.

Usage:

```bash
./build.sh <top module> <verilog files...>
```

Outputs land in `build/` (`.rtlil`/`.json` per stage) and `show/` (`.svg`).
`build/techmap.rtlil` is the final netlist the Java side consumes; the script
prints its location when done. The build exits nonzero if the design uses
anything without a Minecraft mapping (for example latches).

`tests/rtlil/` holds the committed reference netlist of each passing test
(the Java demos read these by default). After changing the flow, rebuild and
copy the fresh `build/techmap.rtlil` over the matching reference file.

Tests (run manually, check exit code):

```bash
./build.sh reductions tests/rtl/test_techmap_ureduce.sv   # reduce ops -> MC_U*
./build.sh counters tests/rtl/test_techmap_dff.sv         # 48-bit reset counters
./build.sh counters_en tests/rtl/test_techmap_dffe.sv     # enable/no-reset counters
./build.sh latches tests/rtl/test_fail_latch.sv           # MUST FAIL (negative test)
```
