# LibPeer-Kotlin
Work in progress implementation of the [LibPeer Standard Protocols](https://saltlamp.pcthingz.com/utdata/LibPeer/LibPeer_Standard_Protocols_v1.pdf) for Kotlin.

This implementation is a port of, and is tested against the [LibPeer Standard Protocols Reference Implementation](https://github.com/Tilo15/LibPeer-Python) written in Python3.

Though this implementation is a port of the above implementation, there are not yet any plans to implement the draft LibPeer Unix Service specification.

Note that this port of LibPeer was started as part of an assignment involving a software project at the Southern Institute of Technology. The project in question is a chat application that will be using this library which is hosted on my project partner's GitHub: https://github.com/Dylan-Erskine/Asfalia 

This implementation has the following dependencies at this stage:
 * RxKotlin

 ## Progress

- [ ] Discoverers
  - [x] Samband
  - [ ] AMPP
- [ ] Networks
  - [x] IPv4
  - [ ] NARP
- [x] Muxer
- [x] Transports
  - [x] EDP
  - [x] DSTP * NB: known issues with timing *
- [ ] Modifiers
- [ ] Interfaces
  - [x] DSI
  - [x] OMI
  - [x] SODI
- [x] Application Utilities

Note that the "application" utilities will be a nice API for applications to use and will not be based on the LibPeer Standard Protocols specification. Rather they will be based on the Python3 reference implementation's API. 