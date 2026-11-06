package com.attgeolocalizao; // ajuste para o seu pacote

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

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements LocationListener {

    private LocationManager locationManager;
    private TextView txtLatitude, txtLongitude, txtPrecisao,
            txtProvedor, txtStatus;
    private MaterialButton btnLocalizacao;
    private LinearProgressIndicator progresso;
    private boolean atualizando = false;
    private Location ultimaLocalizacao;

    // Mapa (OpenStreetMap / osmdroid)
    private MapView mapa;
    private Marker marcador;
    private boolean primeiraLeitura = true;

    // Solicita permissões em tempo de execução
    private final ActivityResultLauncher<String[]> permissaoLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    resultado -> {
                        boolean fine = Boolean.TRUE.equals(resultado.get(
                                Manifest.permission.ACCESS_FINE_LOCATION));
                        boolean coarse = Boolean.TRUE.equals(resultado.get(
                                Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (fine || coarse) {
                            iniciarAtualizacoes();
                        } else {
                            txtStatus.setText("Permissão negada");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuração do osmdroid (deve vir antes de criar o mapa)
        Configuration.getInstance().load(this,
                getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        // User-Agent identifica o app para o servidor de tiles do OSM
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        txtLatitude = findViewById(R.id.txtLatitude);
        txtLongitude = findViewById(R.id.txtLongitude);
        txtPrecisao = findViewById(R.id.txtPrecisao);
        txtProvedor = findViewById(R.id.txtProvedor);
        txtStatus = findViewById(R.id.txtStatus);
        progresso = findViewById(R.id.progresso);
        btnLocalizacao = findViewById(R.id.btnLocalizacao);
        MaterialButton btnMapa = findViewById(R.id.btnMapa);

        configurarMapa();

        // Serviço nativo de localização do Android
        locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

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
            Uri uri = Uri.parse(String.format(Locale.US,
                    "geo:%f,%f?q=%f,%f",
                    ultimaLocalizacao.getLatitude(),
                    ultimaLocalizacao.getLongitude(),
                    ultimaLocalizacao.getLatitude(),
                    ultimaLocalizacao.getLongitude()));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });
    }

    // ---- Mapa ----

    private void configurarMapa() {
        mapa = findViewById(R.id.map);
        mapa.setTileSource(TileSourceFactory.MAPNIK); // mapa padrão do OSM
        mapa.setMultiTouchControls(true);             // zoom com dois dedos

        // Créditos do OpenStreetMap (exigidos pela licença)
        mapa.getOverlays().add(new CopyrightOverlay(this));

        // Visão inicial: Brasil, até a primeira localização chegar
        mapa.getController().setZoom(4.0);
        mapa.getController().setCenter(new GeoPoint(-14.2350, -51.9253));
    }

    private void atualizarMapa(Location loc) {
        GeoPoint posicao = new GeoPoint(loc.getLatitude(), loc.getLongitude());

        if (marcador == null) {
            marcador = new Marker(mapa);
            marcador.setTitle("Você está aqui");
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapa.getOverlays().add(marcador);
        }
        marcador.setPosition(posicao);

        if (primeiraLeitura) {
            // Primeira posição: aproxima o mapa
            mapa.getController().setZoom(17.0);
            primeiraLeitura = false;
        }
        // Acompanha a posição sem mexer no zoom do usuário
        mapa.getController().animateTo(posicao);
        mapa.invalidate();
    }

    // ---- Permissões ----

    private boolean temPermissaoPrecisa() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean temPermissao() {
        return temPermissaoPrecisa()
                || ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void pedirPermissao() {
        permissaoLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    // ---- Localização nativa (LocationManager) ----

    // Inicia as atualizações usando GPS e rede
    // (a rede ajuda em ambientes fechados)
    @SuppressLint("MissingPermission")
    private void iniciarAtualizacoes() {
        boolean algumAtivo = false;

        if (temPermissaoPrecisa() && locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000, 0, this);
            algumAtivo = true;
        }
        if (locationManager
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 2000, 0, this);
            algumAtivo = true;
        }

        if (!algumAtivo) {
            txtStatus.setText("Ative a localização do aparelho");
            return;
        }

        atualizando = true;
        primeiraLeitura = true; // volta a aproximar o mapa na 1ª posição
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
        txtLatitude.setText(String.format(Locale.US,
                "%.6f", loc.getLatitude()));
        txtLongitude.setText(String.format(Locale.US,
                "%.6f", loc.getLongitude()));
        txtPrecisao.setText(String.format(Locale.US,
                "%.1f m", loc.getAccuracy()));
        txtProvedor.setText(loc.getProvider() != null
                ? loc.getProvider().toUpperCase() : "--");
        txtStatus.setText("Localização obtida");
        progresso.setVisibility(android.view.View.INVISIBLE);
        atualizarMapa(loc);
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

    // Necessário para compatibilidade com versões antigas do Android
    @Override
    @SuppressWarnings("deprecation")
    public void onStatusChanged(String provider, int status,
                                Bundle extras) {
    }

    // ---- Ciclo de vida ----

    @Override
    protected void onResume() {
        super.onResume();
        mapa.onResume();
    }

    // Boa prática: parar as atualizações quando o app sai de cena
    @Override
    protected void onPause() {
        super.onPause();
        mapa.onPause();
        if (atualizando) {
            pararAtualizacoes();
        }
    }
}