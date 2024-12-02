#include <string.h>
#include "io.h"
#include "sha256.h"

int main()
{
    char *test_str = "qwiouhguiohqeriuheriughwerihgkjadfsngjkbnklqeufhihqwefg";
    // char *test_str = "qwiouhguiohqeriuheriughwerihgkjadfsngjkbnklqeufhihqwefgq";

    volatile uint8_t *hash = &io_buf_uchar[1000];
    volatile uint8_t *msg = &io_buf_uchar[8];
    volatile unsigned int *msg_len = &io_buf_uint[1];
    volatile unsigned int *ready_flag = &io_buf_uint[0];

    *ready_flag = 1;
    strcpy(msg, test_str);
    *msg_len = strlen(test_str);

    while (1)
    {
        if (*ready_flag)
        {
            *ready_flag = 0;
            sha256(*msg_len, msg, hash);
        }
    }
}
