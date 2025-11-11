package com.contare.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
public class Config {

    @JsonProperty(value = "device")
    private Device device = new Device();

    @Data
    @NoArgsConstructor
    public static class Device {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "MACAddress")
        private String address = "192.168.1.200";

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "ip")
        private String ip = "192.168.1.200";

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "port")
        private int port = 2022;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "antennas")
        private Antennas antennas = new Antennas();

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "verbose")
        private boolean verbose = false;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "frequency")
        private Frequency frequency = new Frequency();

    }

    @Data
    @NoArgsConstructor
    public static class Antennas {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "num")
        private int num = 4;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "map")
        private Map<Integer, Boolean> map = new HashMap<>();

    }


    @Data
    @NoArgsConstructor
    public static class Frequency {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "interval")
        private int interval;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "specs")
        private FrequencySpec[] specs;

    }

    @Data
    @NoArgsConstructor
    public static class FrequencySpec {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "band")
        private Integer band;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "minN")
        private Integer minIndex;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "maxN")
        private Integer maxIndex;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "min")
        private Double min;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "max")
        private Double max;

        public com.contare.chafon.Frequency toFrequency() {
            if (band != null && minIndex != null && maxIndex != null) {
                return com.contare.chafon.Frequency.get(band, minIndex, maxIndex);
            }
            if (min != null && max != null) {
                return com.contare.chafon.Frequency.get(min, max);
            }
            throw new IllegalArgumentException("Frequency spec must contain either (band,minN,maxN) or (minMhz,maxMhz): " + this);
        }

    }

}
