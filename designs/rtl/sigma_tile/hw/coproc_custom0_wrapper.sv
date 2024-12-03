`include "coproc_if.svh"

module coproc_custom0_wrapper (
	input logic unsigned [0:0] clk_i
	, input logic unsigned [0:0] rst_i
	, output logic unsigned [0:0] stream_resp_bus_genfifo_req_o
	, output resp_struct stream_resp_bus_genfifo_wdata_bo
	, input logic unsigned [0:0] stream_resp_bus_genfifo_ack_i
	, input logic unsigned [0:0] stream_req_bus_genfifo_req_i
	, input req_struct stream_req_bus_genfifo_rdata_bi
	, output logic unsigned [0:0] stream_req_bus_genfifo_ack_o
);

assign stream_req_bus_genfifo_ack_o = stream_req_bus_genfifo_req_i;

reg [31:0] result;
assign stream_resp_bus_genfifo_wdata_bo = result;
wire [31:0] arg = stream_req_bus_genfifo_rdata_bi.src0_data;

always @(posedge clk_i) begin
    if (rst_i) begin
        result <= 0;
        stream_resp_bus_genfifo_req_o <= 0;
    end else begin
        stream_resp_bus_genfifo_req_o <= 0;
        if (stream_req_bus_genfifo_req_i) begin
            stream_resp_bus_genfifo_req_o <= 1;

            if (stream_req_bus_genfifo_rdata_bi.src1_data == 0) begin
                result <= rotright(arg, 7) ^ rotright(arg, 18) ^ (arg >> 3);
            end else begin
                result <= rotright(arg, 17) ^ rotright(arg, 19) ^ (arg >> 10);
            end
        end
    end
end

function reg [31:0] rotright(input reg [31:0] a, input reg [4:0] b);
        return ((a >> b) | (a << (32 - b)));
endfunction

endmodule
