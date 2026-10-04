## Channels Packets

RC channels data going from the handset to the transmitter module, and going from the receiver to the flight controller use the same packet type: `CRSF_FRAMETYPE_RC_CHANNELS_PACKED` (0x16). The data is just 16x channels packed to 11 bits (LSB-first, little endian). `[sync] 0x18 0x16 [channel00:11] [channel01:11] ... [channel15:11] [crc8]`.

ExpressLRS: Adds a status byte to control the arm status when transmitting channels from the handset to the TX module. If the packet payload length is 0x18, the status byte is not present and ExpressLRS will use ch4 to trigger "armed" behavior. A payload length 0x19 indicates the last byte contains information to trigger armed behavior (0=disarmed, 1=armed). ExpressLRS >=4.0.0 / EdgeTX v2.11.

Depending on the endianness of your platform, this struct may work to encode/decode the channels data:

```c
struct __attribute__((packed)) crsf_channels_s
{
    unsigned ch0 : 11;
    unsigned ch1 : 11;
    unsigned ch2 : 11;
    unsigned ch3 : 11;
    unsigned ch4 : 11;
    unsigned ch5 : 11;
    unsigned ch6 : 11;
    unsigned ch7 : 11;
    unsigned ch8 : 11;
    unsigned ch9 : 11;
    unsigned ch10 : 11;
    unsigned ch11 : 11;
    unsigned ch12 : 11;
    unsigned ch13 : 11;
    unsigned ch14 : 11;
    unsigned ch15 : 11;
    uint8_t armStatus; // optional ExpressLRS 4.0
};
```

The values are CRSF channel values (0-1984). CRSF 172 represents 988us, CRSF 992 represents 1500us, and CRSF 1811 represents 2012us. Example: All channels set to 1500us (992) `c8 18 16 e0 03 1f f8 c0 07 3e f0 81 0f 7c e0 03 1f f8 c0 07 3e f0 81 0f 7c ad`

```c
// Conversion of CRSF channel value <-> us
crsf = 992 + (8/5 * (us - 1500))
us = 1500 + (5/8 * (crsf - 992))
```

## Example Parsing Code

```cpp
//gcc 7.4.0

#include  <stdio.h>
#include <stdint.h>

static void UnpackChannels(uint8_t const * const payload, uint32_t * const dest)
{
    const unsigned numOfChannels = 16;
    const unsigned srcBits = 11;
    const unsigned dstBits = 32;
    const unsigned inputChannelMask = (1 << srcBits) - 1;

    // code from BetaFlight rx/crsf.cpp / bitpacker_unpack
    uint8_t bitsMerged = 0;
    uint32_t readValue = 0;
    unsigned readByteIndex = 0;
    for (uint8_t n = 0; n < numOfChannels; n++)
    {
        while (bitsMerged < srcBits)
        {
            uint8_t readByte = payload[readByteIndex++];
            readValue |= ((uint32_t) readByte) << bitsMerged;
            bitsMerged += 8;
        }
        //printf("rv=%x(%x) bm=%u\n", readValue, (readValue & inputChannelMask), bitsMerged);
        dest[n] = (readValue & inputChannelMask);
        readValue >>= srcBits;
        bitsMerged -= srcBits;
    }
}

int main(void)
{
    uint32_t dst[16];
    uint8_t src[] = { 
        0xc8, 0x18, 0x16,
        0xe0, 0x03, 0x1f, 0xf8, 0xc0, 0x07, 0x3e, 0xf0, 0x81, 0x0f, 0x7c,
        0xe0, 0x03, 0x1f, 0xf8, 0xc0, 0x07, 0x3e, 0xf0, 0x81, 0x0f, 0x7c, 0xad
    };
    UnpackChannels(&src[3], dst);
    for (unsigned ch=0; ch<16; ++ch)
        printf("ch%02u=%u ", ch, dst[ch]);
    printf("\n");
    return 0;
}
```

```
ch00=992 ch01=992 ch02=992 ch03=992 ch04=992 ch05=992 ch06=992 ch07=992
ch08=992 ch09=992 ch10=992 ch11=992 ch12=992 ch13=992 ch14=992 ch15=992 
```

### Notes

* OpenTX/EdgeTX sends the channels packet starting with 0xEE instead of 0xC8, this has been incorrect since the first CRSF implementation I believe.
