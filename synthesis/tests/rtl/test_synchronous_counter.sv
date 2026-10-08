`default_nettype none

// a four-bit synchronous counter module.

module counter_4bit (
    input  logic       clk,   // clock
    input  logic       rst_n, // resets when off
    output logic [3:0] count, // output count
);

    always @(posedge clk) begin
        if (!rst_n) begin
            count <= 4'b0000;
        end else begin
            count <= count + 1'b1;
        end
    end

endmodule
