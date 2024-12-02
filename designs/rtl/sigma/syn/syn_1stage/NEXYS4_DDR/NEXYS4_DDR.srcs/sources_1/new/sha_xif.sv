`include "sigma_tile.svh"

module sha_xif(
    input clk_i, rst_i,
    input sha_req_i,
    output sha_ack_o,
    input [31:0] sha_addr_i,
    input sha_we_i,
    input [31:0] sha_wdata_i,
    output reg sha_resp_o,
    output [31:0] sha_rdata_o
    );

    reg [31:0] k [0:63] = {
    32'h428a2f98, 32'h71374491, 32'hb5c0fbcf, 32'he9b5dba5, 32'h3956c25b, 32'h59f111f1, 32'h923f82a4, 32'hab1c5ed5,
    32'hd807aa98, 32'h12835b01, 32'h243185be, 32'h550c7dc3, 32'h72be5d74, 32'h80deb1fe, 32'h9bdc06a7, 32'hc19bf174,
    32'he49b69c1, 32'hefbe4786, 32'h0fc19dc6, 32'h240ca1cc, 32'h2de92c6f, 32'h4a7484aa, 32'h5cb0a9dc, 32'h76f988da,
    32'h983e5152, 32'ha831c66d, 32'hb00327c8, 32'hbf597fc7, 32'hc6e00bf3, 32'hd5a79147, 32'h06ca6351, 32'h14292967,
    32'h27b70a85, 32'h2e1b2138, 32'h4d2c6dfc, 32'h53380d13, 32'h650a7354, 32'h766a0abb, 32'h81c2c92e, 32'h92722c85,
    32'ha2bfe8a1, 32'ha81a664b, 32'hc24b8b70, 32'hc76c51a3, 32'hd192e819, 32'hd6990624, 32'hf40e3585, 32'h106aa070,
    32'h19a4c116, 32'h1e376c08, 32'h2748774c, 32'h34b0bcb5, 32'h391c0cb3, 32'h4ed8aa4a, 32'h5b9cca4f, 32'h682e6ff3,
    32'h748f82ee, 32'h78a5636f, 32'h84c87814, 32'h8cc70208, 32'h90befffa, 32'ha4506ceb, 32'hbef9a3f7, 32'hc67178f2}; 

    reg [31:0] state [0:7];
    reg [31:0] tmp [0:7];

    reg [5:0] cnt;
    reg new_bytes;
    reg [31:0] byte_buf;
    reg [31:0] addr_buf;

    reg [31:0] CHefg;
    reg [31:0] EP1;
    reg [31:0] EP0;
    reg [31:0] MAJ;

    wire [31:0] t1 = tmp[7] + EP1 + CHefg + k[cnt] + byte_buf;
    wire [31:0] t2 = EP0 + MAJ;

    assign sha_ack_o = !new_bytes || !sha_we_i;
    assign sha_rdata_o = state[addr_buf[2:0]];

    always @(posedge clk_i) begin
        if (rst_i) begin
            cnt <= 0;
            new_bytes <= 0;
            sha_resp_o <= 0;
        end else begin
            new_bytes <= 0;
            sha_resp_o <= 0;

            if (sha_req_i && !sha_we_i) begin
                sha_resp_o <= 1;
                addr_buf <= sha_addr_i;
            end
            
            if (sha_req_i && sha_we_i && !new_bytes) begin
                new_bytes <= 1;
                byte_buf <= sha_wdata_i;
                addr_buf <= sha_addr_i;

                CHefg <= ((tmp[4] & tmp[5]) ^ (~tmp[4] & tmp[6]));
                EP1 <= rotright(tmp[4], 6) ^ rotright(tmp[4], 11) ^ rotright(tmp[4], 25);
                EP0 <= rotright(tmp[0], 2) ^ rotright(tmp[0], 13) ^ rotright(tmp[0], 22);
                MAJ <= (tmp[0] & tmp[1]) ^ (tmp[0] & tmp[2]) ^ (tmp[1] & tmp[2]);
            end

            if (new_bytes) begin
                if (addr_buf != 8) begin
                    state[addr_buf] <= byte_buf;
                    tmp[addr_buf] <= byte_buf;
                    cnt <= 0;
                end else begin
                    if (cnt == 63) begin
                        cnt <= 0;

                        state[0] <= state[0] + t1 + t2;
                        state[1] <= state[1] + tmp[0];
                        state[2] <= state[2] + tmp[1];
                        state[3] <= state[3] + tmp[2];
                        state[4] <= state[4] + tmp[3] + t1;
                        state[5] <= state[5] + tmp[4];
                        state[6] <= state[6] + tmp[5];
                        state[7] <= state[7] + tmp[6];

                        tmp[0] <= state[0] + t1 + t2;
                        tmp[1] <= state[1] + tmp[0];
                        tmp[2] <= state[2] + tmp[1];
                        tmp[3] <= state[3] + tmp[2];
                        tmp[4] <= state[4] + tmp[3] + t1;
                        tmp[5] <= state[5] + tmp[4];
                        tmp[6] <= state[6] + tmp[5];
                        tmp[7] <= state[7] + tmp[6];
                    end else begin
                        cnt <= cnt + 1;

                        tmp[0] <= t1 + t2;
                        tmp[1] <= tmp[0];
                        tmp[2] <= tmp[1];
                        tmp[3] <= tmp[2];
                        tmp[4] <= tmp[3] + t1;
                        tmp[5] <= tmp[4];
                        tmp[6] <= tmp[5];
                        tmp[7] <= tmp[6];
                    end
                end
            end 
        end
    end

    function reg [31:0] rotright(input reg [31:0] a, input reg [4:0] b);
        return ((a >> b) | (a << (32 - b)));
    endfunction
    
endmodule
