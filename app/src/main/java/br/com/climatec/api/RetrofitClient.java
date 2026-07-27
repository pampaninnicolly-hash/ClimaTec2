package br.com.climatec.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton thread-safe responsável por fornecer a instância única do Retrofit
 * configurada para a API Open-Meteo.
 *
 * Utiliza o padrão Double-Checked Locking para garantir segurança em ambientes
 * multi-thread sem overhead desnecessário após a inicialização.
 */
public class RetrofitClient {

    private static final String BASE_URL = "https://api.open-meteo.com/";

    // volatile garante visibilidade entre threads
    private static volatile Retrofit retrofit = null;

    // Construtor privado impede instanciação direta
    private RetrofitClient() {}

    /**
     * Retorna a instância única do Retrofit (Double-Checked Locking).
     */
    private static Retrofit getInstance() {
        if (retrofit == null) {
            synchronized (RetrofitClient.class) {
                if (retrofit == null) {
                    retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }

    /**
     * Cria e retorna uma implementação da interface {@link APIService}.
     *
     * @return Instância de APIService pronta para uso
     */
    public static APIService getApiService() {
        return getInstance().create(APIService.class);
    }
}