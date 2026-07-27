package br.com.climatec;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.climatec.api.APIService;
import br.com.climatec.api.HttpResponse;
import br.com.climatec.api.RetrofitClient;
import br.com.climatec.models.WeatherCurrent;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Activity principal do aplicativo Climatec.
 *
 * Fluxo de localização:
 *  1. Verifica/solicita a permissão ACCESS_FINE_LOCATION.
 *  2. Tenta obter a última localização em cache via getLastLocation().
 *  3. Se o cache estiver vazio (GPS nunca usado / desligado), dispara uma
 *     requisição ativa com requestLocationUpdates() usando LocationCallback —
 *     o update é descartado após a primeira leitura para economizar bateria.
 *  4. Com as coordenadas em mãos, executa em paralelo:
 *       • Reverse-geocoding (Geocoder) em thread de fundo → exibe nome da cidade.
 *       • Chamada HTTP para a API Open-Meteo → exibe temperatura, umidade e vento.
 *
 * Requisito de build:
 *   implementation 'com.google.android.gms:play-services-location:21.0.0' (ou superior)
 */
public class MainActivity extends AppCompatActivity {

    // ─── Views ────────────────────────────────────────────────────────────────

    private TextView    tvCity;
    private TextView    tvTemperature;
    private TextView    tvHumidity;
    private TextView    tvWindSpeed;
    private Button      btnRefresh;
    private ProgressBar progressBar;

    // ─── Localização ──────────────────────────────────────────────────────────

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback            locationCallback;

    /** Código para identificar a resposta do diálogo de permissão. */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    // ─── API ──────────────────────────────────────────────────────────────────

    /** Parâmetros solicitados ao endpoint /v1/forecast da Open-Meteo. */
    private static final String CURRENT_PARAMS =
            "temperature_2m,relative_humidity_2m,wind_speed_10m";

    // ─── Threading ────────────────────────────────────────────────────────────

    /**
     * Executor de thread única para operações de I/O (Geocoder).
     * O Geocoder.getFromLocation() pode bloquear a thread — nunca chamá-lo
     * na Main Thread para evitar ANR.
     */
    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    // ═════════════════════════════════════════════════════════════════════════
    // Ciclo de vida
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationCallback();

        // Ao abrir o app, inicia o processo de localização
        checkLocationPermissionAndFetch();

        // Botão de atualizar reinicia o processo completo
        btnRefresh.setOnClickListener(v -> checkLocationPermissionAndFetch());
    }

    /**
     * Remove as atualizações de localização ao sair da tela.
     * Evita callbacks chegando em uma Activity parada (memory leak).
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    /** Libera o executor ao destruir a Activity. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. Inicialização das Views
    // ═════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        tvCity        = findViewById(R.id.tvCity);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvHumidity    = findViewById(R.id.tvHumidity);
        tvWindSpeed   = findViewById(R.id.tvWindSpeed);
        btnRefresh    = findViewById(R.id.btnRefresh);
        progressBar   = findViewById(R.id.progressBar);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. Configuração do LocationCallback (fallback para GPS ativo)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Cria o LocationCallback usado quando getLastLocation() retorna null.
     * Remove as atualizações após a primeira leitura (setMaxUpdates(1) já faz
     * isso no request, mas o removeLocationUpdates garante caso o sistema
     * ignore esse limite em versões mais antigas).
     */
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    handleLocation(location);
                }
                // Descarta o listener após o primeiro fix para economizar bateria
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. Verificação e solicitação de permissão
    // ═════════════════════════════════════════════════════════════════════════

    /** Ponto de entrada do fluxo; verifica se a permissão já foi concedida. */
    private void checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
            return;
        }

        // Se o usuário negou uma vez, mostra um diálogo explicativo antes de pedir novamente
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            showPermissionRationale();
        } else {
            // Primeira vez solicitando (ou "Nunca perguntar novamente" foi marcado → Settings)
            requestLocationPermission();
        }
    }

    /** Solicita ACCESS_FINE_LOCATION e ACCESS_COARSE_LOCATION (fallback de rede). */
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    /**
     * Diálogo mostrado quando a permissão foi negada anteriormente mas o
     * usuário ainda não marcou "Nunca perguntar novamente".
     */
    private void showPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Permissão de Localização")
                .setMessage(
                        "O Climatec precisa da sua localização para exibir o clima da sua cidade. "
                        + "Nenhum dado de localização é armazenado ou compartilhado.")
                .setPositiveButton("Conceder", (dialog, which) -> requestLocationPermission())
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    tvCity.setText("Localização negada");
                    Toast.makeText(this,
                            "Sem permissão de localização, o app não pode funcionar.",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. Callback da solicitação de permissão do sistema
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) return;

        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else {
            // Verificar se o usuário marcou "Nunca perguntar novamente" → abrir Configurações
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                openAppSettings();
            } else {
                tvCity.setText("Localização indisponível");
                Toast.makeText(this,
                        "Permissão negada. Localização indisponível.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Redireciona o usuário às configurações do app quando a permissão foi negada permanentemente. */
    private void openAppSettings() {
        Toast.makeText(this,
                "Habilite a permissão de localização nas Configurações do app.",
                Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5. Obtenção da localização
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Estratégia em duas etapas:
     *  • getLastLocation()  → localização em cache (resposta imediata, pode ser nula).
     *  • requestFreshLocation() → usado como fallback quando o cache está vazio.
     */
    private void fetchLocation() {
        // Guarda extra de permissão exigida pelo lint do Android Studio
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        setLoadingState(true);
        tvCity.setText("Obtendo localização…");

        fusedLocationClient.getLastLocation().addOnCompleteListener(task -> {
            Location location = (task.isSuccessful()) ? task.getResult() : null;

            if (location != null) {
                // Cache disponível: usa imediatamente
                handleLocation(location);
            } else {
                // Cache vazio (GPS nunca usado / recém ligado): solicita fix ativo
                requestFreshLocation();
            }
        });
    }

    /**
     * Solicita uma única atualização de localização ativa.
     * Chamado apenas quando getLastLocation() retornar null.
     *
     * Configurações do LocationRequest:
     *  • PRIORITY_HIGH_ACCURACY → usa GPS (mais preciso, maior consumo de bateria)
     *  • interval = 10 s         → intervalo desejado entre atualizações
     *  • minUpdateInterval = 5 s → intervalo mínimo (evita updates excessivos)
     *  • maxUpdates = 1          → descarta o listener após o primeiro fix
     */
    private void requestFreshLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        tvCity.setText("Aguardando sinal GPS…");

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5_000L)
                .setMaxUpdates(1)
                .build();

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6. Processamento da localização obtida
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Ponto central que recebe um objeto Location válido.
     * Dispara em paralelo:
     *  • resolveCity()      → nome da cidade (I/O em background)
     *  • fetchWeatherData() → dados climáticos (Retrofit, thread própria)
     */
    private void handleLocation(Location location) {
        double latitude  = location.getLatitude();
        double longitude = location.getLongitude();

        resolveCity(latitude, longitude);
        fetchWeatherData(latitude, longitude);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 7. Reverse-geocoding (background thread)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Converte coordenadas em nome de cidade usando o Geocoder do Android.
     * IMPORTANTE: Geocoder.getFromLocation() é uma operação de I/O bloqueante —
     * é executada no executor de background e o resultado é postado na Main Thread.
     *
     * Hierarquia de fallback do nome:
     *   SubAdminArea (ex: "São Paulo") → Locality (ex: "Pinheiros") → fallback genérico.
     */
    private void resolveCity(double latitude, double longitude) {
        executor.execute(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            String cityName;

            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);

                    // SubAdminArea costuma trazer o município; Locality traz bairro/cidade
                    cityName = address.getSubAdminArea() != null
                            ? address.getSubAdminArea()
                            : address.getLocality();

                    if (cityName == null || cityName.isEmpty()) {
                        cityName = "Localização desconhecida";
                    }
                } else {
                    cityName = "Cidade não encontrada";
                }
            } catch (IOException e) {
                e.printStackTrace();
                cityName = "Erro ao obter cidade";
            }

            // Retorna à Main Thread para atualizar a UI
            final String result = cityName;
            mainHandler.post(() -> tvCity.setText(result));
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 8. Requisição à API Open-Meteo
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Chama o endpoint /v1/forecast da Open-Meteo com as coordenadas atuais.
     * A ordem dos argumentos respeita a assinatura de APIService:
     *   getCurrentWeather(latitude, longitude, currentParams).
     */
    private void fetchWeatherData(double latitude, double longitude) {
        APIService apiService = RetrofitClient.getApiService();

        // ATENÇÃO: os parâmetros na interface APIService devem seguir a ordem
        //          (latitude, longitude) — confira APIService.java.
        Call<HttpResponse> call =
                apiService.getCurrentWeather(latitude, longitude, CURRENT_PARAMS);

        call.enqueue(new Callback<HttpResponse>() {
            @Override
            public void onResponse(@NonNull Call<HttpResponse> call,
                                   @NonNull Response<HttpResponse> response) {
                setLoadingState(false);

                if (response.isSuccessful() && response.body() != null) {
                    WeatherCurrent current = response.body().getCurrent();

                    tvTemperature.setText(String.format(Locale.getDefault(),
                            "%.1f ºC", current.getTemperature()));
                    tvHumidity.setText(String.format(Locale.getDefault(),
                            "%d %%", current.getRelativeHumidity()));
                    tvWindSpeed.setText(String.format(Locale.getDefault(),
                            "%.1f km/h", current.getWindSpeed()));
                } else {
                    Toast.makeText(MainActivity.this,
                            "Erro na API: código " + response.code(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<HttpResponse> call,
                                  @NonNull Throwable t) {
                setLoadingState(false);
                Toast.makeText(MainActivity.this,
                        "Falha na conexão: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 9. Helpers de UI
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Ativa/desativa o estado de carregamento:
     *  • Exibe/oculta o ProgressBar.
     *  • Desabilita o botão de atualizar durante o carregamento para evitar
     *    chamadas simultâneas desnecessárias.
     */
    private void setLoadingState(boolean loading) {
        btnRefresh.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
