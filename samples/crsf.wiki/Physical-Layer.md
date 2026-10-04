CRSF supports both UART-based and I2C connections for communicating between endpoints. However, AFAIK:

* Receivers: Full Duplex (Half Duplex is not supported)
* Transmitters
  * Internal Modules: Full Duplex
  * External Modules: Half Duplex
* Nothing uses I2C

## UART Based

The most common CRSF devices use UART-based communication, which itself is divided into two classes, depending on the number of wires and communication desired.

### UART Full Duplex

Full duplex CRSF uses two wires, RX and TX, normal polarity (idle high, bit low=0, bit high=1) and has no flow control. Commonly used by:

* Receiver to Flight Controller - For receivers, only the receiver's TX line is required if telemetry is not needed
* Handset to Internal Transmitter Module - For transmitter modules, only the RX line is required if telemetry and mixer sync (CRSFShot) is not needed

### UART Half Duplex

Half Duplex CRSF is a single wire carrying data in both directions. Half duplex connections **are typically INVERTED polarity** (idle low, bit low=1, bit high=0) but have no flow control. In half duplex mode, both sides must be careful to schedule their transmissions to not overlap each other, which would result in the transmitted data being lost.

* Handset to External Transmitter Module (S.PORT) - Sending channels data, the handset is the master for scheduling when each side can transmit. The handset can transmit **at most one** packet per interval. Once the external transmitter module completely receives this packet, it can immediately flip to become the transmitter. While it is the transmitter, the external module may transmit as many packets as will fit in the remaining window of the packet period. Packets that are too large to fit in a single window (due to low baud rates) **must** be split across multiple intervals. Multiple packets can be sent back-to-back with no inter-packet delay so long as the combined total transmit time does not exceed the window duration **or** a maximum of 128 bytes per packet period.

### UART Baud Rate

* Standard receiver baud [is reported to be 416666 baud](https://github.com/tbs-fpv/freedomtx/issues/26#issuecomment-1731202619), however Betaflight / iNav / ExpressLRS use 420000 baud (CRSF v2).
  * CRSFv3 defines a [[baud negotiation protocol | Receiver-Baud-Negotiation]] which may be used (initially at the standard baud).
* EdgeTX and ExpressLRS transmitter modules support 115200, 400000, 921600, 1.87M, 2.25M, 3.75M, 5.25M baud. The baud is set on the handset side and the module automatically switch to adapt to the handset's rate. For any 1 second period, if the number of packets failing CRC check exceeds the number of packets passing, autobaud is performed. For STM32-based transmitter modules, the transmitter cycles through each baud rate, trying it for 1 second and trying the next baud rate if the check fails. For ESP32-based transmitter modules, the hardware autobaud registers can be used to approximate the incoming baud rate and the closest standard rate from the list above should be used (**not** the directly measured value). ExpressLRS ESP32 transmitters also attempt to autodetect if the signal is inverted in both half and full duplex mode.

## I2C Based

"_Some devices use Multimaster mode I2C_" at 100kHz rate. I2C is a bus system using two wires SDA and SCL with pullups on both lines and [[7-bit addresses | CRSF Addresses]]. 