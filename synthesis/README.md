# Synthesis

Currently, most of this is copied from [V2MC](https://github.com/Kenny2github/V2MC). 
This will be customized once I get a working understanding of how it works.

In order to run, [OSS CAD Suite](https://github.com/YosysHQ/oss-cad-suite-build) 
must be installed and added to the PATH. On my computer, I can just run
`source ~/Codes/oss-cad-suite/environment` because that's where I installed it. 

As a basic test, try running `./build.sh reductions tests/rtl/test_techmap_ureduce.sv`
and seeing what shows up in the `build` and `show` directories.
`build/techmap.rtlil` should contain the RTLIL output of the synthesis, and 
should contain enough information to theoretically build the design.