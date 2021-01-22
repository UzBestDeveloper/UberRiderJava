package com.developer.uberriderjava.callbacks;

import com.developer.uberriderjava.models.DriverGeoModel;

public interface IFirebaseDriverInfoListener {
    void onDriverInfoLoadSuccess(DriverGeoModel driverGeoModel);
}
