`default_nettype none

// used to test techmapping of no-reset and enable-only registers ($dff/$dffe)

module counters_en (clk, en, plain_count, en_count, en_wide, neg_count, toggle);
	parameter W = 8;
	parameter WIDE = 48;

	input logic clk, en;
	output logic [W-1:0] plain_count, en_count, neg_count;
	output logic [WIDE-1:0] en_wide;
	output logic toggle;

	// $dff: no reset, no enable
	always_ff @(posedge clk)
		plain_count <= plain_count + 1'b1;

	// $dffe: enable only; leaked as $_DFFE_PP_ before the \$dffe techmap rule
	always_ff @(posedge clk)
		if (en) en_count <= en_count + 1'b1;

	// $dffe wider than 31 bits: chunks into multiple MC_DFF31
	always_ff @(posedge clk)
		if (en) en_wide <= en_wide + 1'b1;

	// negedge clock: CLK_POLARITY path, normalized with an inverter
	always_ff @(negedge clk)
		neg_count <= neg_count + 1'b1;

	// W=1 edge case
	always_ff @(posedge clk)
		toggle <= ~toggle;
endmodule
