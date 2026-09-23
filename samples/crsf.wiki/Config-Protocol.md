### CRSF Config Protocol

Sequence:

* Host sends [[CRSF_FRAMETYPE_DEVICE_PING]] to discover connected devices, and determine the number of configurable values available.
* All devices matching the Extended Destination field of the PING request respond with their [[CRSF_FRAMETYPE_DEVICE_INFO]] packets.
* For each parameter index
  * Host queries parameter index with [[CRSF_FRAMETYPE_PARAMETER_READ]], chunk 0
  * Device responds with [[CRSF_FRAMETYPE_PARAMETER_SETTINGS_ENTRY]] chunk
  * Host may need to request additional chunks to complete the parameter information if the SETTINGS_ENTRY indicated non-zero chunks remaining
* To set a value, host sends an [[CRSF_FRAMETYPE_PARAMETER_WRITE]] with updated value
  * ExpressLRS: Host should requery all same-level parameters to determine if the parameter change has altered other sibling parameter values / options

# Example loading
A full config load from tx module's perspective
```
IN: C8 04 28 00 EA 54 2B C0                         ..(.кT+А
OUT: C8 1C 29 EA EE                               ..)ко
53 49 59 49 20 46 4D 33 30 00 45 4C 52 53 00 00 SIYI FM30.ELRS..
00 00 00 00 00 00 13 00                         ........
CA

IN: C8 06 2C EE EF 01 00 76                         ..,оп..v
OUT: C8 3E 2B EA EE                               .>+ко
01 01 00 09 50 61 63 6B 65 74 20 52 61 74 65 00 ....Packet Rate.
35 30 28 2D 31 31 37 64 62 6D 29 3B 31 35 30 28 50(-117dbm);150(
2D 31 31 32 64 62 6D 29 3B 32 35 30 28 2D 31 30 -112dbm);250(-10
38 64 62 6D 29 3B 35 30 30 28                   8dbm);500(
E5

IN: C8 06 2C EE EF 01 01 A3                         ..,оп..Ј
OUT: C8 16 2B EA EE                               ..+ко
01 00 2D 31 30 35 64 62 6D 29 00 02 00 03 00 48 ..-105dbm).....H
7A 00                                           z.
E6

IN: C8 06 2C EE EF 02 00 6B                         ..,оп..k
OUT: C8 3E 2B EA EE                               .>+ко
02 00 00 09 54 65 6C 65 6D 20 52 61 74 69 6F 00 ....Telem Ratio.
4F 66 66 3B 31 3A 31 32 38 3B 31 3A 36 34 3B 31 Off;1:128;1:64;1
3A 33 32 3B 31 3A 31 36 3B 31 3A 38 3B 31 3A 34 :32;1:16;1:8;1:4
3B 31 3A 32 00 02 00 07 00 00                   ;1:2......
29

IN: C8 06 2C EE EF 03 00 60                         ..,оп..`
OUT: C8 21 2B EA EE                               к!+ко
03 00 00 09 42 54 20 54 65 6C 65 6D 65 74 72 79 ....BT Telemetry
00 4F 66 66 3B 4F 6E 00 00 00 01 00 00          .Off;On......
4C

IN: C8 06 2C EE EF 04 00 51                         ..,оп..Q
OUT: C8 25 2B EA EE                               .%+ко
04 00 00 09 53 77 69 74 63 68 20 4D 6F 64 65 00 ....Switch Mode.
48 79 62 72 69 64 3B 57 69 64 65 00 01 00 01 00 Hybrid;Wide.....
00                                              .
BF

IN: C8 06 2C EE EF 05 00 5A                         ..,оп..Z
OUT: C8 20 2B EA EE                               . +ко
05 00 00 09 4D 6F 64 65 6C 20 4D 61 74 63 68 00 ....Model Match.
4F 66 66 3B 4F 6E 00 00 00 01 00 00             Off;On......
D4

IN: C8 06 2C EE EF 06 00 47                         ..,оп..G
OUT: C8 11 2B EA EE                               ..+ко
06 00 00 0B 54 58 20 50 6F 77 65 72 00          ....TX Power.
75

IN: C8 06 2C EE EF 07 00 4C                         ..,оп..L
OUT: C8 2A 2B EA EE                               .*+ко
07 00 06 09 4D 61 78 20 50 6F 77 65 72 00 31 30 ....Max Power.10
3B 32 35 3B 35 30 3B 31 30 30 3B 32 35 30 00 04 ;25;50;100;250..
00 04 00 6D 57 00                               ...mW.
7B

IN: C8 06 2C EE EF 08 00 25                         ..,оп..%
OUT: C8 33 2B EA EE                               .3+ко
08 00 06 09 44 79 6E 61 6D 69 63 00 4F 66 66 3B ....Dynamic.Off;
4F 6E 3B 41 55 58 39 3B 41 55 58 31 30 3B 41 55 On;AUX9;AUX10;AU
58 31 31 3B 41 55 58 31 32 00 01 00 05 00 00    X11;AUX12......
C9

IN: C8 06 2C EE EF 09 00 2E                         ..,оп...
OUT: C8 1A 2B EA EE                               ..+ко
09 00 00 0B 56 54 58 20 41 64 6D 69 6E 69 73 74 ....VTX Administ
72 61 74 6F 72 00                               rator.
30

IN: C8 06 2C EE EF 0A 00 33                         ..,оп..3
OUT: C8 22 2B EA EE                               ."+ко
0A 00 09 09 42 61 6E 64 00 4F 66 66 3B 41 3B 42 ....Band.Off;A;B
3B 45 3B 46 3B 52 3B 4C 00 05 00 06 00 00       ;E;F;R;L......
E3

IN: C8 06 2C EE EF 0B 00 38                         ..,оп..8
OUT: C8 25 2B EA EE                               .%+ко
0B 00 09 09 43 68 61 6E 6E 65 6C 00 31 3B 32 3B ....Channel.1;2;
33 3B 34 3B 35 3B 36 3B 37 3B 38 00 00 00 07 00 3;4;5;6;7;8.....
00                                              .
66

IN: C8 06 2C EE EF 0C 00 09                         ..,оп...
OUT: C8 27 2B EA EE                               .'+ко
0C 00 09 09 50 77 72 20 4C 76 6C 00 2D 3B 31 3B ....Pwr Lvl.-;1;
32 3B 33 3B 34 3B 35 3B 36 3B 37 3B 38 00 00 00 2;3;4;5;6;7;8...
08 00 00                                        ...
50

IN: C8 06 2C EE EF 0D 00 02                         ..,оп...
OUT: C8 1C 2B EA EE                               ..+ко
0D 00 09 09 50 69 74 6D 6F 64 65 00 4F 66 66 3B ....Pitmode.Off;
4F 6E 00 00 00 01 00 00                         On......
13

IN: C8 06 2C EE EF 0E 00 1F                         ..,оп...
OUT: C8 14 2B EA EE                               ..+ко
0E 00 09 0D 53 65 6E 64 20 56 54 78 00 00 C8 00 ....Send VTx..И.
BE

IN: C8 06 2C EE EF 0F 00 14                         ..,оп...
OUT: C8 1A 2B EA EE                               ..+ко
0F 00 00 0B 57 69 46 69 20 43 6F 6E 6E 65 63 74 ....WiFi Connect
69 76 69 74 79 00                               ivity.
81

IN: C8 06 2C EE EF 10 00 CD                         ..,оп..Н
OUT: C8 1A 2B EA EE                               ..+ко
10 00 0F 0D 45 6E 61 62 6C 65 20 52 78 20 57 69 ....Enable Rx Wi
46 69 00 00 C8 00                               Fi..И.
B6

IN: C8 06 2C EE EF 11 00 C6                         ..,оп..Ж
OUT: C8 10 2B EA EE                               ..+ко
11 00 00 0D 42 69 6E 64 00 00 C8 00             ....Bind..И.
9D

IN: C8 06 2C EE EF 12 00 DB                         ..,оп..Ы
OUT: C8 17 2B EA EE                               ..+ко
12 00 00 8C 42 61 64 2F 47 6F 6F 64 00 30 2F 32 ...Bad/Good.0/2
35 30 00                                        50.
27

IN: C8 06 2C EE EF 13 00 D0                         ..,оп..Р
OUT: C8 1D 2B EA EE                               ..+ко
13 00 00 0C 6D 61 73 74 65 72 20 49 53 4D 32 47 ....master ISM2G
34 00 38 32 35 65 64 38 00                      4.825ed8.
4A
```
