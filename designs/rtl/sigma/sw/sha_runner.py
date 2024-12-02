# -*- coding:utf-8 -*-
from __future__ import division

import sys
from hashlib import sha256

sys.path.append('../../udm/sw')
import udm
from udm import *

import sigma
from sigma import *

__SHA_ADDR_HASH = 0x63E8
__SHA_ADDR_MSG = 0x6008
__SHA_ADDR_MSG_LEN = 0x6004
__SHA_ADDR_RDY_FLAG = 0x6000

udm = udm('/dev/ttyUSB1', 921600)
print("")

sigma = sigma(udm)

sigma.tile.loadelf("apps/sha256.riscv")

time.sleep(2)

while (True):
    string = input()
    sigma.tile.udm.wr32(__SHA_ADDR_MSG_LEN, len(string))

    datawords = []
    tmp = 0
    cnt = 0
    for i in range(len(string)):
        tmp |= (bytes(string, 'UTF-8')[i]) << (cnt * 8)
        if (cnt == 3):
            datawords.append(tmp)
            tmp = 0
            cnt = 0
        else:
            cnt += 1
    datawords.append(tmp)

    sigma.tile.udm.wrarr32(__SHA_ADDR_MSG, datawords)
    sigma.tile.udm.wr32(__SHA_ADDR_RDY_FLAG, 1)

    time.sleep(2)

    hash_fpga = sigma.tile.udm.rdarr32(__SHA_ADDR_HASH, 8)
    ref = sha256(bytes(string, 'UTF-8'))

    flag = True
    print("Hash from FPGA: ", end = '')
    for i in range(32):
        hash_fpga_byte = (hash_fpga[i / 4] >> (8 * (i % 4))) & 0xFF
        ref_byte = ref[i]

        if (hash_fpga_byte != ref_byte):
            flag = False
        
        print(f'{hash_fpga_byte:02x}', end = '')
    
    print("\nReference     :", ref.hexdigest())
    if flag:
        print("Match")
    else:
        print("No match")
    print()

udm.disconnect()
