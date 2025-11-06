package com.contare.core.objects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString
public class TagMetadata {

    private final String epc;
    private final Integer rssi;
    private final Integer antenna;
    private final String device;
    private final String memId;

}
