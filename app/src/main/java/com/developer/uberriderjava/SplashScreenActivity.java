package com.developer.uberriderjava;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.developer.uberriderjava.databinding.ActivitySplashScreenBinding;
import com.developer.uberriderjava.models.RiderModel;
import com.developer.uberriderjava.utils.UserUtils;
import com.firebase.ui.auth.AuthMethodPickerLayout;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class SplashScreenActivity extends AppCompatActivity {

    ActivitySplashScreenBinding binding;

    private final static int LOGIN_REQUEST_CODE = 7171;
    private List<AuthUI.IdpConfig> providerGoogle;
    private List<AuthUI.IdpConfig> providerPhone;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener listener;
    FirebaseDatabase database;
    DatabaseReference riderInfoRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        init();
    }

    private void init() {

        database = FirebaseDatabase.getInstance();
        riderInfoRef = database.getReference(Common.RIDER_INFO_REFERENCE);

        providerGoogle = Collections.singletonList(new AuthUI.IdpConfig.GoogleBuilder().build());
        providerPhone = Collections.singletonList(new AuthUI.IdpConfig.PhoneBuilder().build());

        firebaseAuth = FirebaseAuth.getInstance();
        listener = myFirebaseAuth -> {
            FirebaseUser user = myFirebaseAuth.getCurrentUser();
            if (user != null) {

                //update token
                FirebaseInstanceId.getInstance().getInstanceId()
                        .addOnFailureListener(e -> Toast.makeText(SplashScreenActivity.this,e.getMessage(),Toast.LENGTH_SHORT).show()).addOnSuccessListener(instanceIdResult -> {
                    Log.d("TOKEN",instanceIdResult.getToken());
                    UserUtils.updateToken(SplashScreenActivity.this,instanceIdResult.getToken());
                });

                checkUserFromFirebase();
            } else {
                showLoginLayout();
            }
        };
    }

    private void showRegisterLayout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);
        View itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null);

        TextInputEditText edit_first_name = itemView.findViewById(R.id.edt_first_name);
        TextInputEditText edit_last_name = itemView.findViewById(R.id.edt_last_name);
        TextInputEditText edit_phone_number = itemView.findViewById(R.id.edt_phone_number);

        Button btn_continue = itemView.findViewById(R.id.btn_register);

        if (FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber() != null &&
                !TextUtils.isEmpty(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber())) {
            edit_phone_number.setText(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber());
        }

        builder.setView(itemView);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        btn_continue.setOnClickListener(v -> {
            if (TextUtils.isEmpty(edit_first_name.getText().toString())) {
                Toast.makeText(this, "Please enter first name", Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(edit_last_name.getText().toString())) {
                Toast.makeText(this, "Please enter last name", Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(edit_phone_number.getText().toString())) {
                Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show();
            } else {
                RiderModel model = new RiderModel();
                model.setFirstName(edit_first_name.getText().toString());
                model.setLastName(edit_last_name.getText().toString());
                model.setPhoneNumber(edit_phone_number.getText().toString());

                riderInfoRef.child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                        .setValue(model)
                        .addOnFailureListener(e -> {
                            alertDialog.dismiss();
                            Toast.makeText(SplashScreenActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        })
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(SplashScreenActivity.this, "Register successfully", Toast.LENGTH_SHORT).show();
                            alertDialog.dismiss();
                            goToHomeActivity(model);
                        });
            }
        });
    }

    private void showLoginLayout() {
        AuthMethodPickerLayout authMethodPickerLayout = new AuthMethodPickerLayout
                .Builder(R.layout.layout_sign_in)
                .setPhoneButtonId(R.id.btn_phone_sign_in)
                .setGoogleButtonId(R.id.btn_google_sign_in)
                .build();

        startActivityForResult(AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setIsSmartLockEnabled(false)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providerPhone)
                .setAvailableProviders(providerGoogle)
                .build(), LOGIN_REQUEST_CODE
        );


    }

    private void checkUserFromFirebase() {
        riderInfoRef.child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
//                            Toast.makeText(SplashScreenActivity.this, "User already registered", Toast.LENGTH_SHORT).show();
                            RiderModel model = snapshot.getValue(RiderModel.class);
                            goToHomeActivity(model);
                        } else {
                            showRegisterLayout();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SplashScreenActivity.this, "" + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToHomeActivity(RiderModel model) {
        Common.currentRider = model;
        startActivity(new Intent(SplashScreenActivity.this, HomeActivity.class));
        finish();
    }

    @SuppressLint("CheckResult")
    private void delaySplashScreen() {

        binding.progressBar.setVisibility(View.VISIBLE);

        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .subscribe(() -> firebaseAuth.addAuthStateListener(listener));
    }

    @Override
    protected void onStart() {
        super.onStart();
        delaySplashScreen();
    }

    @Override
    protected void onStop() {
        if (firebaseAuth != null && listener != null) {
            firebaseAuth.removeAuthStateListener(listener);
        }
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOGIN_REQUEST_CODE) {

            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            } else {
                Toast.makeText(this, "[ERROR]: " + response.getError().getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

}