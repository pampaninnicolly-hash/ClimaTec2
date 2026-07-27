package br.com.climatec.models;

import com.google.gson.annotations.SerializedName;

/**
 * Modelo que representa o bloco "current" da resposta da API Open-Meteo.
 *
 * Os campos são mapeados automaticamente pelo Gson via {@link SerializedName}.
 * A anotação @Expose foi removida pois o GsonConverterFactory padrão não usa
 * excludeFieldsWithoutExposeAnnotation(), tornando-a redundante.
 */
public class WeatherCurrent {

    /** Temperatura a 2 metros do solo, em graus Celsius */
    @SerializedName("temperature_2m")
    private double temperature;

    /** Umidade relativa do ar a 2 metros, em porcentagem (0–100) */
    @SerializedName("relative_humidity_2m")
    private int relativeHumidity;

    /** Velocidade do vento a 10 metros do solo, em km/h */
    @SerializedName("wind_speed_10m")
    private double windSpeed;

    /**
     * Código WMO de condição climática.
     * Referência: https://open-meteo.com/en/docs#weathervariables
     * Ex: 0 = céu limpo, 61 = chuva leve, 95 = trovoada
     */
    @SerializedName("weather_code")
    private int weatherCode;

    // --- Getters ---

    public double getTemperature() { return temperature; }
    public int getRelativeHumidity() { return relativeHumidity; }
    public double getWindSpeed() { return windSpeed; }
    public int getWeatherCode() { return weatherCode; }
}