### Subset Channels (0x17)

CRSFv3

* uint8_t config byte
  * bits 0-4 (5 bits) First channel number appearing in this packet (0 = ch1)
  * bits 5-6 (2 bits) Channel resolution
    * 0 (b00) - 10 bits/channel
    * 1 (b01) - 11 bits/channel
    * 2 (b10) - 12 bits/channel
    * 3 (b11) - 13 bits/channel
  * bit 7 (1 bit) reserved configuration bit
* data[] - channel data packed

The subset channels packet allows for the sending channels data in arbitrary precision and in greater quantity than the legacy [[CRSF_FRAMETYPE_RC_CHANNELS_PACKED]] packet. The channels data can be 10-13 bit, but all represent values from 998us to 2012us. 

| Bits/Channel | Packet Value | us Value |
|--|--|--|
| 10 | 1 | 989us |
| 11 | 1 | 988.5us |
| 12 | 1 | 988.25us |
| 13 | 1 | 988.125us |

All channels data is packed -- each channel value is not byte aligned. The number of channels present in the packet is implied from the packet length.
