package com.contare.core.objects;

import com.contare.chafon.Frequency;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class Options {

    public final String address;   // device MAC Address
    public final String ip;        // network IP Address
    public final int port;         // network port
    public final int antennas;     // number of antennas
    public final boolean verbose;  // enables verbose mode
    public final List<Frequency> frequencies;
    public final int interval;

    public Options(final String address, final String ip, final Integer port, final Integer antennas, final boolean verbose, final List<Frequency> frequencies, final Integer interval) {
        this.address = address;
        this.ip = ip;
        this.port = (port != null) ? port : 0;
        this.antennas = (antennas != null) ? antennas : 4;
        this.verbose = verbose;
        this.frequencies = frequencies;
        this.interval = (interval != null) ? interval : 1_000;
    }

}
