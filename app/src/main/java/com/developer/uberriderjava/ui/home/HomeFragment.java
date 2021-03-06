package com.developer.uberriderjava.ui.home;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.developer.uberriderjava.Common;
import com.developer.uberriderjava.callbacks.IFirebaseDriverInfoListener;
import com.developer.uberriderjava.callbacks.IFirebaseFailedListener;
import com.developer.uberriderjava.connect.RetrofitClient;
import com.developer.uberriderjava.models.AnimationModel;
import com.developer.uberriderjava.models.DriverGeoModel;
import com.developer.uberriderjava.models.DriverInfoModel;
import com.developer.uberriderjava.models.GeoQueryModel;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.developer.uberriderjava.R;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class HomeFragment extends Fragment implements OnMapReadyCallback, IFirebaseFailedListener, IFirebaseDriverInfoListener {

    private HomeViewModel homeViewModel;
    private GoogleMap mMap;

    //Location
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    SupportMapFragment mapFragment;

    //Load Driver
    private double distance = 1.0;
    private static final double LIMIT_RANGE = 10.0;
    private Location previousLocation, currentLocation;

    //Listener
    IFirebaseDriverInfoListener iFirebaseDriverInfoListener;
    IFirebaseFailedListener iFirebaseFailedListener;

    private boolean firstTime = true;
    private String cityName;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private com.developer.uberriderjava.connect.iGoogleApi iGoogleApi;



    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        init();
        return root;
    }

    private void init() {

        iGoogleApi = RetrofitClient.getInstance().create(com.developer.uberriderjava.connect.iGoogleApi.class);

        iFirebaseDriverInfoListener = this;
        iFirebaseFailedListener = this;

        locationRequest = new LocationRequest();
        locationRequest.setSmallestDisplacement(10f);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                LatLng newPosition = new LatLng(locationResult.getLastLocation().getLatitude(), locationResult.getLastLocation().getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPosition, 18f));

                //If user changes location,calculate and load driver again
                if (firstTime) {
                    previousLocation = currentLocation = locationResult.getLastLocation();
                    firstTime = false;
                } else {
                    previousLocation = currentLocation;
                    currentLocation = locationResult.getLastLocation();
                }

                if (previousLocation.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE) {
                    loadAvailableDrivers();
                }
            }
        };

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext());
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(mapFragment.getView(), getString(R.string.location_permission_required), Snackbar.LENGTH_SHORT).show();
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

        loadAvailableDrivers();
    }

    private void loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(getView(), getString(R.string.location_permission_required), Snackbar.LENGTH_SHORT).show();
            return;
        }
        fusedLocationProviderClient.getLastLocation()
                .addOnFailureListener(e -> Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show())
                .addOnSuccessListener(location -> {
                    //Load all drivers in city
                    Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                    List<Address> addressList;

                    DecimalFormat df = new DecimalFormat();
                    df.setMaximumFractionDigits(3);

                    try {
                        double lat = Double.parseDouble(df.format(location.getLatitude()).replace(",","."));

                        double lon = Double.parseDouble(df.format(location.getLongitude()).replace(",","."));
                        addressList = geocoder.getFromLocation(lat, lon, 1);
                        cityName = addressList.get(0).getLocality();

                        //Query
                        DatabaseReference driver_loc_ref = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCES).child(cityName);
                        GeoFire gf = new GeoFire(driver_loc_ref);
                        GeoQuery geoQuery = gf.queryAtLocation(new GeoLocation(location.getLatitude(), location.getLongitude()), distance);
                        geoQuery.removeAllListeners();

                        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                            @Override
                            public void onKeyEntered(String key, GeoLocation location) {
                                Common.driversFound.add(new DriverGeoModel(key, location));
                            }

                            @Override
                            public void onKeyExited(String key) {

                            }

                            @Override
                            public void onKeyMoved(String key, GeoLocation location) {

                            }

                            @Override
                            public void onGeoQueryReady() {
                                if (distance <= LIMIT_RANGE) {
                                    distance++;
                                    loadAvailableDrivers();
                                } else {
                                    distance = 1.0;
                                    addDriverMarker();
                                }
                            }

                            @Override
                            public void onGeoQueryError(DatabaseError error) {
                                Snackbar.make(getView(), error.getMessage(), Snackbar.LENGTH_SHORT).show();
                            }
                        });

                        driver_loc_ref.addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                                GeoQueryModel model = snapshot.getValue(GeoQueryModel.class);
                                GeoLocation geoLocation = new GeoLocation(model.getL().get(0), model.getL().get(1));
                                DriverGeoModel driverGeoModel = new DriverGeoModel(snapshot.getKey(), geoLocation);
                                Location newDriverLocation = new Location("");
                                newDriverLocation.setLatitude(geoLocation.latitude);
                                newDriverLocation.setLongitude(geoLocation.longitude);
                                float newDistance = location.distanceTo(newDriverLocation) / 1000;
                                if (newDistance <= LIMIT_RANGE) {
                                    findDriverByKey(driverGeoModel);
                                }
                            }

                            @Override
                            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                            }

                            @Override
                            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

                            }

                            @Override
                            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {

                            }
                        });

                    } catch (IOException e) {
                        e.printStackTrace();
                        Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show();
                    }

                });
    }

    @SuppressLint("CheckResult")
    private void addDriverMarker() {
        if (Common.driversFound.size() > 0) {
            Observable.fromIterable(Common.driversFound)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(driverGeoModel -> findDriverByKey(driverGeoModel), throwable -> Snackbar.make(getView(), throwable.getMessage(), Snackbar.LENGTH_SHORT).show(), () -> {
                    });
        } else {
            Snackbar.make(getView(), getString(R.string.drivers_not_found), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void findDriverByKey(DriverGeoModel driverGeoModel) {
        FirebaseDatabase.getInstance()
                .getReference(Common.DRIVER_INFO_REFERENCE)
                .child(driverGeoModel.getKey())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.hasChildren()) {
                            driverGeoModel.setDriverInfoModel(snapshot.getValue(DriverInfoModel.class));
                            iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel);
                        } else {
                            iFirebaseFailedListener.onFirebaseLoadFailed(getString(R.string.driver_with_key_not_found));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        iFirebaseFailedListener.onFirebaseLoadFailed(error.getMessage() + driverGeoModel.getKey());
                    }
                });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        Dexter.withContext(requireContext())
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            Snackbar.make(getView(), getString(R.string.location_permission_required), Snackbar.LENGTH_SHORT).show();
                            return;
                        }
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setZoomControlsEnabled(false);
                        mMap.setOnMyLocationButtonClickListener(() -> {
                            fusedLocationProviderClient.getLastLocation()
                                    .addOnFailureListener(e -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show())
                                    .addOnSuccessListener(location -> {
                                        LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 18f));
                                    });
                            return true;
                        });

                        ///set layout button
                        View locationButton = ((View) mapFragment.getView().findViewById(Integer.parseInt("1"))
                                .getParent()).findViewById(Integer.parseInt("2"));

                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
                        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                        params.setMargins(0, 0, 0, 250);

                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        Toast.makeText(requireContext(), "Permission " + permissionDeniedResponse.getPermissionName()
                                + "" + " was denied!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {

                    }
                }).check();

        mMap.getUiSettings().setZoomControlsEnabled(true);

        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.uber_maps_style));
            if (!success) {
                Log.e("UBER_ERROR", "Style parsing error");
            }
        } catch (Resources.NotFoundException e) {
            Log.e("UBER_ERROR", e.getMessage());
        }

    }

    @Override
    public void onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        super.onDestroy();
    }

    @Override
    public void onFirebaseLoadFailed(String message) {
        Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onDriverInfoLoadSuccess(DriverGeoModel driverGeoModel) {
        if (!Common.markerList.containsKey(driverGeoModel.getKey())) {
            Common.markerList.put(driverGeoModel.getKey(),
                    mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(driverGeoModel.getGeoLocation().latitude, driverGeoModel.getGeoLocation().longitude))
                            .flat(true)
                            .title(Common.buildName(driverGeoModel.getDriverInfoModel().getFirstName(), driverGeoModel.getDriverInfoModel().getLastName()))
                            .snippet(driverGeoModel.getDriverInfoModel().getPhoneNumber())
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                    ));

            if (!TextUtils.isEmpty(cityName)) {
                DatabaseReference driverLocation = FirebaseDatabase.getInstance()
                        .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                        .child(cityName)
                        .child(driverGeoModel.getKey());
                driverLocation.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.hasChildren()) {
                            if (Common.markerList.get(driverGeoModel.getKey()) != null)
                                Common.markerList.get(driverGeoModel.getKey()).remove();
                            Common.markerList.remove(driverGeoModel.getKey());
                            Common.driverLocationSubscribe.remove(driverGeoModel.getKey());
                            driverLocation.removeEventListener(this);
                        } else {
                            if (Common.markerList.get(driverGeoModel.getKey()) != null) {
                                GeoQueryModel geoQueryModel = snapshot.getValue(GeoQueryModel.class);
                                AnimationModel animationModel = new AnimationModel(false, geoQueryModel);
                                if (Common.driverLocationSubscribe.get(driverGeoModel.getKey()) != null) {
                                    Marker currentMarker = Common.markerList.get(driverGeoModel.getKey());
                                    AnimationModel oldPosition = Common.driverLocationSubscribe.get(driverGeoModel.getKey());
                                    String from =
                                            oldPosition.getGeoQueryModel().getL().get(0) +
                                                    "," +
                                                    oldPosition.getGeoQueryModel().getL().get(1);
                                    String to =
                                            animationModel.getGeoQueryModel().getL().get(0) +
                                                    "," +
                                                    animationModel.getGeoQueryModel().getL().get(1);

                                    moveMarkerAnimation(driverGeoModel.getKey(), animationModel, currentMarker, from, to);

                                } else {
                                    Common.driverLocationSubscribe.put(driverGeoModel.getKey(), animationModel);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Snackbar.make(getView(), error.getMessage(), Snackbar.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void moveMarkerAnimation(String key, AnimationModel animationModel, Marker currentMarker, String from, String to) {
        if (!animationModel.isRun()) {
            compositeDisposable.add(iGoogleApi.getDirections(
                    "driving",
                    "less_driving",
                    from,
                    to,
                    getString(R.string.google_api_key)
                    ).subscribeOn(Schedulers.io()).
                            observeOn(AndroidSchedulers.mainThread())
                            .subscribe(returnResult -> {
                                Log.d("API_RETURN", returnResult);

                                try {
                                    JSONObject jsonObject = new JSONObject(returnResult);
                                    JSONArray jsonArray = jsonObject.getJSONArray("routes");
                                    for (int i = 0; i < jsonArray.length(); i++) {
                                        JSONObject route = jsonArray.getJSONObject(i);
                                        JSONObject poly = route.getJSONObject("overview_polyline");
                                        String polyLine = poly.getString("points");
//                                        polyLineList = Common.decodePoly(polyLine);
                                        animationModel.setPolyLineList(Common.decodePoly(polyLine));
                                    }

                                    //Moving
//                                    index = -1;
//                                    next = 1;
                                    animationModel.setIndex(-1);
                                    animationModel.setNext(1);
                                    Runnable runnable = new Runnable() {
                                        @Override
                                        public void run() {
                                            if (null != animationModel.getPolyLineList() && animationModel.getPolyLineList().size() > 1) {
                                                if (animationModel.getIndex() < animationModel.getPolyLineList().size() - 2) {
                                                    animationModel.setIndex(animationModel.getIndex()+1);
                                                    animationModel.setNext(animationModel.getIndex()+1);
                                                    animationModel.setStart(animationModel.getPolyLineList().get(animationModel.getIndex()));
                                                    animationModel.setEnd(animationModel.getPolyLineList().get(animationModel.getNext()));
                                                }

                                                ValueAnimator valueAnimator = ValueAnimator.ofInt(0, 1);
                                                valueAnimator.setDuration(3000);
                                                valueAnimator.setInterpolator(new LinearInterpolator());
                                                valueAnimator.addUpdateListener(value -> {
                                                    animationModel.setV(value.getAnimatedFraction());
                                                    animationModel.setLat(animationModel.getV() * animationModel.getEnd().latitude + (1 - animationModel.getV()) * animationModel.getStart().latitude);
                                                    animationModel.setLng(animationModel.getV() * animationModel.getEnd().longitude + (1 - animationModel.getV()) * animationModel.getStart().longitude);
                                                    LatLng newPos = new LatLng(animationModel.getLat(), animationModel.getLng());
                                                    currentMarker.setPosition(newPos);
                                                    currentMarker.setAnchor(0.5f, 0.5f);
                                                    currentMarker.setRotation(Common.getBearing(animationModel.getStart(), newPos));
                                                });

                                                valueAnimator.start();
                                                if (animationModel.getIndex() < animationModel.getPolyLineList().size() - 2) {
                                                    animationModel.getHandler().postDelayed(this, 1500);
                                                } else if (animationModel.getIndex() < animationModel.getPolyLineList().size() - 1) {
                                                    animationModel.setRun(false);
                                                    Common.driverLocationSubscribe.put(key, animationModel);
                                                }
                                            }
                                        }
                                    };

                                    animationModel.getHandler().postDelayed(runnable, 1500);

                                } catch (Exception e) {
                                    Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show();
                                }
                            })
            );
        }
    }

    @Override
    public void onStop() {
        compositeDisposable.clear();
        super.onStop();
    }
}