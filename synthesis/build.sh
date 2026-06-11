#!/bin/bash
set -euo pipefail

if [ $# -lt 2 ]; then
	echo "usage: $0 <top module> <verilog files...>" >&2
	exit 1
fi

TOP=$1
shift

mkdir -p build show

# generate the yosys script and run it
trap 'rm -f tmp.ys' EXIT
{
	echo "read_verilog -sv $*"
	# simlib first so designs may instantiate MC_* cells directly
	echo "read_verilog -lib rtl/mc_simlib.sv"
	echo "hierarchy -check -top $TOP"
	cat synth_build.ys
} > tmp.ys
yosys -s tmp.ys

# todo: copy the final netlist somewhere permanent (e.g. tests/rtlil) instead
# of leaving it in the throwaway build dir
echo ""
echo "final netlist written to $(pwd)/build/techmap.rtlil"
