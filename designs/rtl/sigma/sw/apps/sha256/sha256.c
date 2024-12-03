#include "sha256.h"
#include "sha_xif.h"

static uint32_t m[64];
static uint32_t datalen;
static uint32_t bitlen;
static uint32_t m_tmp;

static void sha256_consume_byte(uint8_t data)
{
    m_tmp |= (uint32_t)data << (24 - (((datalen - 1) % 4) << 3));
    if (datalen % 4 == 0 && datalen != 0)
    {
        m[datalen / 4 - 1] = m_tmp;
        sha_xif_push_new_bytes(m_tmp);
        m_tmp = 0;
    }
    if (datalen == 64)
    {
        for (int i = 16; i < 64; ++i)
        {
            m[i] = sig(m[i - 2], 1) + m[i - 7] + sig(m[i - 15], 0) + m[i - 16];
            sha_xif_push_new_bytes(m[i]);
        }
    }
}

static void sha256_init()
{
    datalen = 0;
    bitlen = 0;

    sha_xif_init();

    for (int i = 0; i < 64; i++)
        m[i] = 0;
    m_tmp = 0;
}

static void sha256_final(uint8_t *hash)
{
    int i;
    // Pad whatever data is left in the buffer.
    if (datalen < 56)
    {
        datalen++;
        sha256_consume_byte(0x80);
        while (datalen < 56)
        {
            datalen++;
            sha256_consume_byte(0x00);
        }
    }
    else
    {
        datalen++;
        sha256_consume_byte(0x80);
        while (datalen < 64)
        {
            datalen++;
            sha256_consume_byte(0x00);
        }
        for (i = 0; i < 14; i++)
        {
            m[i] = 0;
            sha_xif_push_new_bytes(0);
        }
        datalen = 56;
    }
    // Append to the padding the total message's length in bits and transform.
    for (i = 0; i < 4; i++)
    {
        datalen++;
        sha256_consume_byte(0);
    }
    for (i = 0; i < 4; i++)
    {
        datalen++;
        sha256_consume_byte((bitlen >> ((7 - i) << 3)) & 0xff);
    }
    for (i = 0; i < 32; i++)
    {
        hash[i] = (sha_xif_read_state(i / 4) >> (24 - (i % 4) * 8)) & 0xff;
    }
}

void sha256(uint32_t msg_len, uint8_t *msg, uint8_t *hash)
{
    sha256_init();

    for (uint32_t i = 0; i < msg_len; i++)
    {
        datalen++;
        bitlen += 8;
        sha256_consume_byte(msg[i]);
        datalen %= 64;
    }

    sha256_final(hash);
}
