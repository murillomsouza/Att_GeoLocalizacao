package com.attgeolocalizao;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private LocationManager locationManager;
    private TextView txtLatitude, txtLongitude, txtPrecisao, txtProvedor, txtStatus;
    private MaterialButton btnLocalizacao;
    private LinearProgressIndicator progresso;
    private boolean atualizando = false;
    private Location ultimaLocalizacao;

    // Solicita permissões em tempo de execução
    private final ActivityResultLauncher<String[]> permissaoLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    resultado -> {
                        boolean fine = Boolean.TRUE.equals(
                                resultado.get(Manifest.permission.ACCESS_FINE_LOCATION));
                        boolean coarse = Boolean.TRUE.equals(
                                resultado.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (fine || coarse) {
                            iniciarAtualizacoes();
                        } else {
                            txtStatus.setText("Permissão negada");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(getResources().getColor(R.color.base, getTheme()));

        txtLatitude = findViewById(R.id.txtLatitude);
        txtLongitude = findViewById(R.id.txtLongitude);
        txtPrecisao = findViewById(R.id.txtPrecisao);
        txtProvedor = findViewById(R.id.txtProvedor);
        txtStatus = findViewById(R.id.txtStatus);
        progresso = findViewById(R.id.progresso);
        btnLocalizacao = findViewById(R.id.btnLocalizacao);
        MaterialButton btnMapa = findViewById(R.id.btnMapa);

        // Serviço nativo de localização do Android
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        btnLocalizacao.setOnClickListener(v -> {
            if (!temPermissao()) {
                pedirPermissao();
                return;
            }
            if (atualizando) {
                pararAtualizacoes();
            } else {
                iniciarAtualizacoes();
            }
        });

        btnMapa.setOnClickListener(v -> {
            if (ultimaLocalizacao == null) {
                txtStatus.setText("Obtenha a localização primeiro");
                return;
            }
            Uri uri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f",
                    ultimaLocalizacao.getLatitude(), ultimaLocalizacao.getLongitude(),
                    ultimaLocalizacao.getLatitude(), ultimaLocalizacao.getLongitude()));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });
    }

    private boolean temPermissaoPrecisa() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean temPermissao() {
        return temPermissaoPrecisa()
                || ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void pedirPermissao() {
        permissaoLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    // Inicia as atualizações usando GPS e rede (a rede ajuda em ambientes fechados)
    @SuppressLint("MissingPermission")
    private void iniciarAtualizacoes() {
        boolean algumAtivo = false;

        if (temPermissaoPrecisa()
                && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000, 0, this);
            algumAtivo = true;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 2000, 0, this);
            algumAtivo = true;
        }

        if (!algumAtivo) {
            txtStatus.setText("Ative a localização do aparelho");
            return;
        }

        atualizando = true;
        btnLocalizacao.setText("Parar localização");
        txtStatus.setText("Buscando sinal...");
        progresso.setVisibility(android.view.View.VISIBLE);
    }

    private void pararAtualizacoes() {
        locationManager.removeUpdates(this);
        atualizando = false;
        btnLocalizacao.setText("Obter localização");
        txtStatus.setText("Atualizações paradas");
        progresso.setVisibility(android.view.View.INVISIBLE);
    }

    private void exibirLocalizacao(Location loc) {
        ultimaLocalizacao = loc;
        txtLatitude.setText(String.format(Locale.US, "%.6f", loc.getLatitude()));
        txtLongitude.setText(String.format(Locale.US, "%.6f", loc.getLongitude()));
        txtPrecisao.setText(String.format(Locale.US, "%.1f m", loc.getAccuracy()));
        txtProvedor.setText(loc.getProvider() != null ? loc.getProvider().toUpperCase() : "--");
        txtStatus.setText("Localização obtida");
        progresso.setVisibility(android.view.View.INVISIBLE);
    }

    // ---- Métodos da interface LocationListener ----

    @Override
    public void onLocationChanged(@NonNull Location location) {
        exibirLocalizacao(location);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        txtStatus.setText(provider + " ativado");
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        txtStatus.setText(provider + " desativado");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (atualizando) {
            pararAtualizacoes();
        }
    }
}