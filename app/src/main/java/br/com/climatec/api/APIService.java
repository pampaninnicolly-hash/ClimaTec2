package br.com.climatec.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Interface Retrofit que mapeia os endpoints da API Open-Meteo.
 * Base URL: https://api.open-meteo.com/
 *
 * Documentação oficial: https://open-meteo.com/en/docs
 */
public interface APIService {

    /**
     * Retorna dados meteorológicos atuais para as coordenadas fornecidas.
     *
     * Endpoint: GET /v1/forecast
     *
     * Exemplo de URL gerada:
     *   https://api.open-meteo.com/v1/forecast
     *     ?latitude=-23.55
     *     &longitude=-46.63
     *     &current=temperature_2m,relative_humidity_2m,wind_speed_10m
     *
     * CORREÇÃO: parâmetros estavam invertidos (longitude antes de latitude),
     * o que enviava coordenadas trocadas para a API.
     * A ordem correta é latitude → longitude, espelhando a convenção geográfica
     * e a chamada em MainActivity.java.
     *
     * @param latitude      Latitude do local (ex: -23.55 para São Paulo)
     * @param longitude     Longitude do local (ex: -46.63 para São Paulo)
     * @param currentParams Campos a retornar, separados por vírgula
     *                      (ex: "temperature_2m,relative_humidity_2m,wind_speed_10m")
     * @return Call com o corpo mapeado em {@link HttpResponse}
     */
    @GET("v1/forecast")
    Call<HttpResponse> getCurrentWeather(
            @Query("latitude")  double latitude,
            @Query("longitude") double longitude,
            @Query("current")   String currentParams
    );
}
