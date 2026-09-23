# Official CRSF Protocol Specifications

TBS has published official CRSF specifications publicly, which obsolete these specifications:

https://github.com/tbs-fpv/tbs-crsf-spec/blob/main/crsf.md

## Unofficial CRSF Protocol Specifications

This documentation for the CRSF protocol is the collected opinions of CapnBry, created by reverse engineering or reading the source code of various open source projects and is the basis of the implementation in the ExpressLRS project. _Only by first understanding the existing protocol can we seek to build upon it._

* [[Physical Layer]] - Serial configuration and such
* [[Message Format]]
* [[CRSF Addresses]] - Addresses used for SRC/DEST fields
* [[Packet Types]]
* [[Config Protocol]] - How the Lua configuration script works
* [[Receiver Baud Negotiation]]

### Examples

* [[Python Parser]] - A simple python 3 CRSF parser
* [Arduino Library](https://github.com/CapnBry/CRServoF/tree/main/lib/CrsfSerial) - Simple Arduino platform library