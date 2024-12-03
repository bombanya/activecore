#ifndef __SHA25_H__
#define __SHA25_H__

#include <stdint.h>
#include <stdbool.h>

void sha256(uint32_t msg_len, uint8_t *msg, uint8_t *hash);

inline unsigned int sig(unsigned int x, unsigned int n)
{
    unsigned int result;
    asm volatile(".insn r 0x0b, 0x0, 0x0, %0, %1, %2"
                 : "=r"(result)
                 : "r"(x), "r"(n));
    return result;
}

#endif
