`default_nettype none

// negative test: latches have no Minecraft mapping, so this build MUST FAIL
// (the $_DLATCH_P_ cell trips the whitelist assertion in synth_build.ys)

module latches (sel, d, q);
	input logic sel, d;
	output logic q;

	always_latch
		if (sel) q = d;
endmodule
