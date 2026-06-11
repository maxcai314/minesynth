/**
 * Parser and immutable data model for the Yosys RTLIL text format.
 *
 * <p>{@link ax.xz.max.minesynth.rtlil.RtlilParser} turns an {@code .rtlil} file
 * (as written by Yosys {@code write_rtlil}) into an {@link ax.xz.max.minesynth.rtlil.RtlilDesign}.
 * The model mirrors RTLIL itself: a design holds modules; a module holds wires,
 * memories, cells, processes, and connections. The full statement grammar is
 * supported, including pre-{@code proc} constructs (processes, memories), even
 * though the minesynth flow only feeds post-techmap netlists downstream.
 *
 * <p>Conventions used throughout this package:
 * <ul>
 * <li>Identifiers keep their RTLIL prefix verbatim: {@code \name} is a public
 *     name, {@code $name} is Yosys-generated. Note that auto-generated names may
 *     contain brackets and dots (for example {@code $flatten\foo.$xor$x.sv:5$8_Y}
 *     or {@code $..._collate[1]}); they are only delimited by whitespace.</li>
 * <li>Bit order is LSB-first everywhere in the model ({@link ax.xz.max.minesynth.rtlil.BitVector},
 *     {@link ax.xz.max.minesynth.rtlil.SigSpec} chunks and bits). The text format
 *     writes constants and concatenations MSB-first; the parser converts.</li>
 * <li>Slice indexes in the text format are source-level (affected by a wire's
 *     {@code offset} and {@code upto} flags); the model stores plain physical
 *     bit offsets, converted exactly the way Yosys's own parser does.</li>
 * </ul>
 *
 * <p>All model types are immutable records; the parser is the only producer,
 * but nothing stops tools from constructing designs programmatically (see
 * {@link ax.xz.max.minesynth.rtlil.RtlilWriter}).
 */
package ax.xz.max.minesynth.rtlil;
