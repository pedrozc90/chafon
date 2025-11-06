package com.contare.core;

import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.objects.Options;

import java.io.Closeable;

public interface RfidDevice extends Closeable {

    boolean init(final Options opts) throws RfidDeviceException;

    boolean start();

    boolean stop();

}
