package com.contare.core.mappers;

import com.contare.core.objects.TagMetadata;
import com.rfid.ReadTag;

public class TagMetadataMapper {

    public static TagMetadata toDto(final ReadTag read) {
        return new TagMetadata(
            read.epcId,
            read.rssi,
            read.antId,
            read.ipAddr,
            read.memId
        );
    }

}
