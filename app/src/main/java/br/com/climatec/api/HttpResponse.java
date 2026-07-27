package br.com.climatec.api;

import com.google.gson.annotations.SerializedName;
import br.com.climatec.models.WeatherCurrent;

/**
 * Modelo que representa o corpo completo da resposta JSON da API Open-Meteo.
 *
 * Exemplo de resposta:
 * <pre>
 * {
 *   "latitude": -23.55,
 *   "longitude": -46.63,
 *   "timezone": "America/Sao_Paulo",
 *   "current": { ... }
 * }
 * </pre>
 */
public class HttpResponse {

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("timezone")
    private String timezone;

    @SerializedName("current")
    private WeatherCurrent current;

    // --- Getters ---

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getTimezone() { return timezone; }
    public WeatherCurrent getCurrent() { return current; }
}