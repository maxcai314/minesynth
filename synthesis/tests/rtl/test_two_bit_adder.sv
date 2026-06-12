`default_nettype none

// a two-bit adder module.

module adder_2bit (
    input  logic [1:0] a,    // 2-bit input A
    input  logic [1:0] b,    // 2-bit input B
    input  logic       cin,  // Carry-in bit
    output logic [1:0] sum,  // 2-bit Sum output
    output logic       cout  // Carry-out bit
);

    // synthesizer will choose gate structure
    assign {cout, sum} = a + b + cin;

endmodule
