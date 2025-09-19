# Minesynth
This project aims to synthesize SystemVerilog into 
Minecraft redstone circuits.

## Planning
Similar to [V2MC](https://github.com/Kenny2github/V2MC/blob/542831e3e2c4a1b5e501c956d596d9098621fe83/v2mc.ys), 
minesynth will use [yosys](https://github.com/YosysHQ/yosys) with 
[technology mappings](https://yosyshq.readthedocs.io/projects/yosys/en/latest/cell_index.html) to generate
[RTLIL](https://blog.eowyn.net/yosys/CHAPTER_TextRtlil.html) designs. 
Then, a Java program will parse that design and solve for the 
placement and routing of redstone components. Finally, 
a [schematic](https://minecraft.wiki/w/Schematic_file_format) file will be generated.