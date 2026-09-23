CRSFv3

Sequence:

* On boot, the receiver should start at the standard baud (416666 / 420000).
* Receiver sends request to change baud rate
  * type `CRSF_FRAMETYPE_COMMAND` (extended)
  * dest `CRSF_ADDRESS_FLIGHT_CONTROLLER`
  * src `CRSF_ADDRESS_CRSF_RECEIVER`
  * Command realm `CRSF_COMMAND_SUBCMD_GENERAL`
  * Subcommand `CRSF_COMMAND_SUBCMD_GENERAL_CRSF_SPEED_REQUEST`
  * [command data](https://github.com/crsf-wg/crsf/wiki/CRSF_FRAMETYPE_COMMAND#crsf_command_subcmd_general_crsf_speed_proposal)
* Flight Controller determines if the baud rate is suitable and sends reply
  * type `CRSF_FRAMETYPE_COMMAND` (extended)
  * dest `CRSF_ADDRESS_CRSF_RECEIVER`
  * src `CRSF_ADDRESS_FLIGHT_CONTROLLER`
  * Command realm `CRSF_COMMAND_SUBCMD_GENERAL`
  * Subcommand `CRSF_COMMAND_SUBCMD_GENERAL_CRSF_SPEED_RESPONSE`
  * response [command data](https://github.com/crsf-wg/crsf/wiki/CRSF_FRAMETYPE_COMMAND#crsf_command_subcmd_general_crsf_speed_response)
* If baud rate was not suitable, Flight Controller ends process here
* Flight controller **must not change baud or send any telemetry** for 4ms
* Flight controller adjusts baud rate to requested value
* Resume normal operation

**NOTE**: In CRSFv3 mode, the flight controller must always send heartbeat packets even if telemetry is disabled.
 
BetaFlight Supported Bauds: 9.6K, 19.2K, 38.4K, 57.6K, 115.2K, 230.4K, 250K, 400K, 460.8K, 500K, 921.6K, 1M, 1.5M, 2M, 2.47M (K=1000, M=1000000). Note that 416666/420K is not supported for baud negotiation after initial connection.

