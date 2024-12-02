#ifndef __SHA25_H__
#define __SHA25_H__

#include <stdint.h>
#include <stdbool.h>

void sha256(uint32_t msg_len, uint8_t *msg, uint8_t *hash);

#endif
