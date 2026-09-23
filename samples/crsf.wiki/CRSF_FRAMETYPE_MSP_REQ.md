Extended Packet

### MSP Request

MSP is the foundation of transferring arbitrary external data into and out of a CRSF system. `CRSF_FRAMETYPE_MSP_REQ` is the _request_ type packet, meaning the sender is requesting an MSP function and is expecting a `CRSF_FRAMETYPE_MSP_RESP` message of the same MSP function in return. Note that CRSF-encapsulated-MSP does not follow the same format as UART-based plain MSP (no `$M>...`).

#### Extended payload (MSPv2)

* uint16_t MSP flags - e.g. 0x50 0x00 = No error, version 2, beginning of frame, first frame
* uint16_t function - Parameter ID of MSP request. e.g. 0x88 0x00 = MSP_VTX_CONFIG
* uint16_t payload length - Length of payload following this header (does not include this or above fields). Usually 0 for a `CRSF_FRAMETYPE_MSP_REQ`.
* uint8_t payload[]
