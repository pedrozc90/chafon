package com.contare.core.objects;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Options {

    public final String address;   // device MAC Address
    public final String ip;        // network IP Address
    public final int port;         // network port
    public final int antennas;     // number of antennas
    public final boolean verbose;  // enables verbose mode

    public Options(final String address, final String ip, final Integer port, final Integer antennas, final boolean verbose) {
        this.address = address;
        this.ip = ip;
        this.port = (port != null) ? port : 0;
        this.antennas = (antennas != null) ? antennas : 4;
        this.verbose = verbose;
    }

}
