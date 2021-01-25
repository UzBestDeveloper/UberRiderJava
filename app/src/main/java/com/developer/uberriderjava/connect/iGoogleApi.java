package com.developer.uberriderjava.connect;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface iGoogleApi {

    @GET("maps/api/directions/json")
    Observable<String> getDirections(
            @Query("mode") String mode,
            @Query("transit_routing_reference") String transit_routing,
            @Query("origin") String from,
            @Query("destination") String to,
            @Query("key") String key
    );
}
