#ifndef __SHA_XIF_H__
#define __SHA_XIF_H__

#include "stdint.h"

#define SHA_ADDR_BEGIN 0x80000040
#define SHA_ADDR_END 0x80000060

static volatile uint32_t *sha_xif_state = (volatile uint32_t *)(SHA_ADDR_BEGIN);
static volatile uint32_t *sha_xif_new_bytes = (volatile uint32_t *)(SHA_ADDR_END);

static inline void sha_xif_init()
{
    sha_xif_state[0] = 0x6a09e667;
    sha_xif_state[1] = 0xbb67ae85;
    sha_xif_state[2] = 0x3c6ef372;
    sha_xif_state[3] = 0xa54ff53a;
    sha_xif_state[4] = 0x510e527f;
    sha_xif_state[5] = 0x9b05688c;
    sha_xif_state[6] = 0x1f83d9ab;
    sha_xif_state[7] = 0x5be0cd19;
}

static inline uint32_t sha_xif_read_state(uint8_t state_n)
{
    return sha_xif_state[state_n];
}

static inline void sha_xif_push_new_bytes(uint32_t new_bytes)
{
    *sha_xif_new_bytes = new_bytes;
}

#endif
